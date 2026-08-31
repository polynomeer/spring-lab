package lab.dashboard.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lab.dashboard.interpret.ScenarioInterpreter;
import lab.tools.jdi.TraceEvent;
import lab.tools.jdi.TracerCommand;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "지금 활성 시나리오는 최대 1개"(docs/plan/03-learning-dashboard-design.md 4번 절)를 그대로
 * 구현한다 - 이미 실행 중인 세션이 있으면 {@link #start}가 그걸 먼저 정리하고 새로 시작한다.
 *
 * <p>이 클래스는 TracerServer를 자식 프로세스로 launch하고, 그 NDJSON stdout을 파싱해서
 * 원본 히트는 {@link RawHitReceived}로, 그 히트로부터 시나리오별 해석기가 파생시킨 것은
 * {@link SemanticEventReceived}로 - {@link ApplicationEventPublisher}를 통해 발행한다.
 * 웹소켓 계층(lab.dashboard.web)은 이 이벤트들을 구독하기만 하면 된다 - 프로세스 관리와
 * 전송 계층을 분리해 둔 것이다.
 */
@Component
public class ScenarioSession {

    // dispatcher-flow(6.4절)나 mvc-exception-priority처럼 "실행 중인 시나리오에 실제 HTTP
    // 요청을 주입"하는 게 필요한 대상이 임베디드 서버를 띄우면, 준비된 포트를 이 한 줄로
    // 알려준다(DispatcherServletTraceLab, ExceptionPipelineLab 참고) - 그 밖의 시나리오는
    // 이 마커를 찍지 않으므로 매칭될 일이 없다.
    private static final Pattern READY_PORT_PATTERN = Pattern.compile("^EMBEDDED_SERVER_READY port=(\\d+)$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration DEFAULT_DYNAMIC_SCENARIO_TIMEOUT = Duration.ofSeconds(120);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher;
    private final Duration dynamicScenarioTimeout;
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "scenario-timeout");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicReference<Running> running = new AtomicReference<>();

    @Autowired
    public ScenarioSession(ApplicationEventPublisher eventPublisher) {
        this(eventPublisher, DEFAULT_DYNAMIC_SCENARIO_TIMEOUT);
    }

    /** 테스트가 실제로 몇 분씩 기다리지 않고도 타임아웃 동작을 검증할 수 있도록 짧은 값을 넣을 수 있게 열어 둔다. */
    ScenarioSession(ApplicationEventPublisher eventPublisher, Duration dynamicScenarioTimeout) {
        this.eventPublisher = eventPublisher;
        this.dynamicScenarioTimeout = dynamicScenarioTimeout;
    }

    public synchronized void start(ScenarioDefinition definition) {
        stop();

        try {
            String javaBin = System.getProperty("java.home") + "/bin/java";
            // TracerServer 자신을 실행할 클래스패스 - 이 백엔드 프로세스 자신의 런타임
            // 클래스패스를 그대로 재사용한다. tools:jdi-tracer가 이 모듈의 의존성이므로
            // TracerServer 클래스와 그 Jackson 의존성이 이미 여기 다 들어 있다.
            String tracerClasspath = System.getProperty("java.class.path");

            List<String> command = new ArrayList<>(List.of(
                    javaBin, "--add-modules", "jdk.jdi", "-cp", tracerClasspath,
                    "lab.tools.jdi.TracerServer",
                    definition.targetClasspath(), definition.mainClass()));
            // definition.breakpointSpec()은 이제 (spec 파일 경로가 아니라) 여러 줄일 수 있는
            // 인라인 텍스트다(docs/plan/04-dynamic-scenario-design.md 3번 절) - TracerServer는
            // 가변 인자로 "각 줄이 하나의 스펙"을 받으므로, 한 줄이면 지금까지와 완전히 같은
            // 인자 1개, 여러 줄이면 인자 여러 개로 풀어서 넘긴다.
            command.addAll(definition.breakpointSpec().lines().filter(line -> !line.isBlank()).toList());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            Process process = builder.start();

            PrintWriter stdin = new PrintWriter(process.getOutputStream(), true);
            Running current = new Running(process, stdin, definition.name(), new AtomicReference<>(),
                    new AtomicReference<>());
            running.set(current);

            // 즉석 코드 작성 시나리오(docs/plan/04-dynamic-scenario-design.md 5번 절)만
            // 대상이다 - 기존 6개 카탈로그 시나리오는 이미 검증된 코드이고, dispatcher-flow처럼
            // 사용자가 직접 요청을 보낼 때까지 무기한 기다리는 것도 있어서 일괄 타임아웃을
            // 걸면 안 된다.
            if (definition.dynamicallyCompiled()) {
                ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(
                        () -> onTimeout(current), dynamicScenarioTimeout.toSeconds(), TimeUnit.SECONDS);
                current.timeoutFuture().set(timeoutFuture);
            }

            ScenarioInterpreter interpreter = definition.interpreterFactory().get();
            Thread reader = new Thread(() -> pump(current, interpreter));
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            throw new IllegalStateException("failed to launch scenario " + definition.name(), e);
        }
    }

    private synchronized void onTimeout(Running target) {
        // 이 타이머가 도는 사이 세션이 이미 자연 종료됐거나 다른 시나리오로 교체됐을 수
        // 있다 - identity로 정확히 지금 이 타이머가 지키던 세션이 맞는지 확인한 뒤에만 죽인다.
        if (running.get() != target) {
            return;
        }
        eventPublisher.publishEvent(new ScenarioTimedOut(target.scenarioName(), dynamicScenarioTimeout.toSeconds()));
        stop();
    }

    public synchronized void sendCommand(String cmd, Long intervalMs) {
        Running current = running.get();
        if (current == null) {
            return;
        }
        sendCommandTo(current, cmd, intervalMs);
    }

    /**
     * dispatcher-flow 시나리오의 "요청 보내기" 버튼이 쓰는 경로 - 지금 실행 중인 시나리오의
     * 임베디드 서버로 실제 HTTP 요청을 쏜다. 응답은 {@link ScenarioHttpResponseReceived}로
     * 발행할 뿐, 여기서 기다리지 않는다 - 우리가 관심 있는 신호는 응답 자체가 아니라 그 요청이
     * 지나가면서 찍는 JDI 히트들이다(step/play로 계속 관찰 중인 그 세션).
     */
    public void sendHttpRequest(String method, String path, String body) {
        Running current = running.get();
        if (current == null) {
            return;
        }
        Integer port = current.readyPort().get();
        if (port == null) {
            eventPublisher.publishEvent(new ScenarioHttpResponseReceived(
                    current.scenarioName(), method, path, null, "target server not ready yet"));
            return;
        }

        HttpRequest.BodyPublisher publisher = (body == null || body.isBlank())
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method.toUpperCase(), publisher);
        if (!(body == null || body.isBlank())) {
            builder.header("Content-Type", "application/json");
        }

        HTTP_CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        eventPublisher.publishEvent(new ScenarioHttpResponseReceived(
                                current.scenarioName(), method, path, null, error.getMessage()));
                    } else {
                        eventPublisher.publishEvent(new ScenarioHttpResponseReceived(
                                current.scenarioName(), method, path, response.statusCode(), null));
                    }
                });
    }

    public synchronized void stop() {
        Running current = running.getAndSet(null);
        if (current == null) {
            return;
        }
        ScheduledFuture<?> timeoutFuture = current.timeoutFuture().get();
        if (timeoutFuture != null) {
            // 세션이 자연 종료되거나 새 세션으로 교체될 때도 stop()을 거치므로, 여기서
            // 취소해 두지 않으면 이미 끝난 세션에 대한 타이머가 나중에 헛되이 또 발화한다
            // (onTimeout()의 identity 체크가 실제로 해를 끼치는 건 막아 주지만, 안 쓰는
            // 타이머를 계속 스케줄러에 남겨 두는 건 낭비다).
            timeoutFuture.cancel(false);
        }
        sendCommandTo(current, TracerCommand.QUIT, null);
        current.process().destroyForcibly();
    }

    private void sendCommandTo(Running target, String cmd, Long intervalMs) {
        try {
            target.stdin().println(mapper.writeValueAsString(new TracerCommand(cmd, intervalMs)));
            target.stdin().flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void pump(Running current, ScenarioInterpreter interpreter) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(current.process().getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(current, interpreter, line);
            }
        } catch (IOException ignored) {
            // stop()이 프로세스를 강제 종료하면 스트림이 그냥 끊긴다 - 정상 종료 경로다.
        }
    }

    private void handleLine(Running current, ScenarioInterpreter interpreter, String line) {
        String scenarioName = current.scenarioName();
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (JsonProcessingException e) {
            return; // TracerServer 프로토콜 밖의 잡음(있다면) - 무시한다.
        }

        switch (node.path("type").asText()) {
            case "hit" -> {
                try {
                    TraceEvent traceEvent = mapper.treeToValue(node.get("event"), TraceEvent.class);
                    eventPublisher.publishEvent(new RawHitReceived(scenarioName, traceEvent));
                    for (var semanticEvent : interpreter.onHit(traceEvent)) {
                        eventPublisher.publishEvent(new SemanticEventReceived(scenarioName, semanticEvent));
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("malformed hit event from TracerServer: " + line, e);
                }
            }
            case "stdout" -> {
                String streamLine = node.path("line").asText();
                Matcher readyPort = READY_PORT_PATTERN.matcher(streamLine);
                if (readyPort.matches()) {
                    current.readyPort().set(Integer.parseInt(readyPort.group(1)));
                }
                eventPublisher.publishEvent(new ScenarioStdoutReceived(
                        scenarioName, node.path("stream").asText(), streamLine));
            }
            case "exited" -> {
                eventPublisher.publishEvent(new ScenarioExited(scenarioName, node.path("totalHits").asInt()));
                ScheduledFuture<?> timeoutFuture = current.timeoutFuture().get();
                if (timeoutFuture != null) {
                    // 정상 종료됐으니 아직 안 울린 타임아웃 타이머가 있다면 헛되이 나중에
                    // 발화하지 않도록 취소한다(무슨 일이 나지는 않지만 - onTimeout()의 identity
                    // 체크가 이미 안전하게 무시한다 - 스케줄러에 계속 남겨 둘 이유가 없다).
                    timeoutFuture.cancel(false);
                }
                running.set(null);
            }
            default -> { }
        }
    }

    private record Running(Process process, PrintWriter stdin, String scenarioName,
                            AtomicReference<Integer> readyPort, AtomicReference<ScheduledFuture<?>> timeoutFuture) {
    }
}
