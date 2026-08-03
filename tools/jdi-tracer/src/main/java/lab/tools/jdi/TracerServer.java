package lab.tools.jdi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link Tracer}와 같은 JDI 브레이크포인트 엔진을, 콘솔 프린트 대신 NDJSON(줄바꿈으로 구분된
 * JSON) 프로토콜로 감싼 서버 모드다 - docs/plan/03-learning-dashboard-design.md의 0단계.
 *
 * <p>표준 출력으로 한 줄에 하나씩 JSON 이벤트를 내보낸다:
 * <pre>{@code
 * {"type":"hit","event":{...TraceEvent...}}
 * {"type":"stdout","stream":"out"|"err","line":"..."}
 * {"type":"exited","totalHits":N}
 * }</pre>
 *
 * <p>표준 입력으로 한 줄에 하나씩 {@link TracerCommand} JSON을 받는다:
 * {@code {"cmd":"step"}}, {@code {"cmd":"play","intervalMs":500}},
 * {@code {"cmd":"pause"}}, {@code {"cmd":"quit"}}.
 *
 * <p>기존 {@link Tracer}는 브레이크포인트에 걸릴 때마다 즉시 {@code resume()}하지만, 이
 * 서버는 히트를 보고한 뒤 다음 "step" 신호가 올 때까지 멈춰 있는다 - 이게 "라이브 실행"을
 * 한 걸음씩(또는 재생 속도로) 들여다볼 수 있게 만드는 핵심 차이다.
 */
public final class TracerServer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PrintStream out;
    private final BlockingQueue<Boolean> stepSignals = new ArrayBlockingQueue<>(64);
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicLong playIntervalMs = new AtomicLong(500);
    private volatile boolean quit = false;
    private volatile Process targetProcess;

    private TracerServer(PrintStream out) {
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: TracerServer <targetClasspath> <targetMainClass> <breakpointSpec|specFile>...");
            System.exit(1);
        }
        new TracerServer(System.out).run(args);
    }

    private void run(String[] args) throws Exception {
        String targetClasspath = args[0];
        String targetMainClass = args[1];
        Map<String, Set<String>> targets = JdiSupport.parseTargets(args, 2);

        VirtualMachine vm = JdiSupport.launch(targetClasspath, targetMainClass);
        this.targetProcess = vm.process();

        EventRequestManager requestManager = vm.eventRequestManager();
        for (String className : targets.keySet()) {
            ClassPrepareRequest request = requestManager.createClassPrepareRequest();
            request.addClassFilter(className);
            request.enable();
        }

        redirectChildOutput(targetProcess.getInputStream(), "out");
        redirectChildOutput(targetProcess.getErrorStream(), "err");
        startCommandReader();
        startPlayTicker();

        EventQueue queue = vm.eventQueue();
        vm.resume();

        boolean connected = true;
        int hitCount = 0;
        while (connected && !quit) {
            EventSet eventSet = queue.remove();
            boolean sawBreakpoint = false;
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent classPrepareEvent) {
                    JdiSupport.enableBreakpoints(requestManager, classPrepareEvent.referenceType(), targets);
                } else if (event instanceof BreakpointEvent breakpointEvent) {
                    hitCount++;
                    sawBreakpoint = true;
                    emitHit(hitCount, breakpointEvent);
                } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                    connected = false;
                }
            }
            if (!connected) {
                break;
            }
            if (sawBreakpoint) {
                awaitStepSignal();
            }
            if (quit) {
                break;
            }
            eventSet.resume();
        }
        targetProcess.waitFor();
        emitExited(hitCount);
    }

    private void awaitStepSignal() {
        try {
            stepSignals.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            quit = true;
        }
    }

    // "play" 모드일 때 설정된 간격마다 step 신호를 대신 넣어 주는, 서버 수명 내내 도는
    // 단일 데몬 스레드다 - play/pause할 때마다 스레드를 새로 만들고 없앨 필요가 없도록
    // 이렇게 단순화했다.
    private void startPlayTicker() {
        Thread ticker = new Thread(() -> {
            while (!quit) {
                try {
                    Thread.sleep(Math.max(50, playIntervalMs.get()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (playing.get()) {
                    stepSignals.offer(Boolean.TRUE);
                }
            }
        });
        ticker.setDaemon(true);
        ticker.start();
    }

    private void startCommandReader() {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    handleCommandLine(line);
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void handleCommandLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        TracerCommand command;
        try {
            command = mapper.readValue(trimmed, TracerCommand.class);
        } catch (JsonProcessingException e) {
            // phase 0 범위에서는 잘못된 명령을 위한 별도 에러 채널을 두지 않는다 - 무시한다.
            return;
        }
        switch (command.cmd()) {
            case TracerCommand.STEP -> stepSignals.offer(Boolean.TRUE);
            case TracerCommand.PLAY -> {
                if (command.intervalMs() != null) {
                    playIntervalMs.set(command.intervalMs());
                }
                playing.set(true);
            }
            case TracerCommand.PAUSE -> playing.set(false);
            case TracerCommand.QUIT -> {
                quit = true;
                playing.set(false);
                if (targetProcess != null) {
                    targetProcess.destroyForcibly();
                }
                stepSignals.offer(Boolean.TRUE); // 메인 루프가 대기 중이었다면 깨운다
            }
            default -> { }
        }
    }

    private void emitHit(int hitId, BreakpointEvent event) throws com.sun.jdi.IncompatibleThreadStateException {
        emit(new HitEnvelope("hit", HitSerializer.toTraceEvent(hitId, event)));
    }

    private void emitExited(int totalHits) {
        emit(new ExitedEnvelope("exited", totalHits));
    }

    private void redirectChildOutput(InputStream in, String stream) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(new StdoutEnvelope("stdout", stream, line));
                }
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void emit(Object envelope) {
        try {
            out.println(mapper.writeValueAsString(envelope));
            out.flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record HitEnvelope(String type, TraceEvent event) {
    }

    private record StdoutEnvelope(String type, String stream, String line) {
    }

    private record ExitedEnvelope(String type, int totalHits) {
    }
}
