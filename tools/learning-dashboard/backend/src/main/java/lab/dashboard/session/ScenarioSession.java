package lab.dashboard.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lab.dashboard.interpret.ScenarioInterpreter;
import lab.tools.jdi.TraceEvent;
import lab.tools.jdi.TracerCommand;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;

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

    private final ObjectMapper mapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<Running> running = new AtomicReference<>();

    public ScenarioSession(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public synchronized void start(ScenarioDefinition definition) {
        stop();

        try {
            String javaBin = System.getProperty("java.home") + "/bin/java";
            // TracerServer 자신을 실행할 클래스패스 - 이 백엔드 프로세스 자신의 런타임
            // 클래스패스를 그대로 재사용한다. tools:jdi-tracer가 이 모듈의 의존성이므로
            // TracerServer 클래스와 그 Jackson 의존성이 이미 여기 다 들어 있다.
            String tracerClasspath = System.getProperty("java.class.path");

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin, "--add-modules", "jdk.jdi", "-cp", tracerClasspath,
                    "lab.tools.jdi.TracerServer",
                    definition.targetClasspath(), definition.mainClass(), definition.breakpointSpec());
            builder.redirectErrorStream(false);
            Process process = builder.start();

            PrintWriter stdin = new PrintWriter(process.getOutputStream(), true);
            Running current = new Running(process, stdin);
            running.set(current);

            ScenarioInterpreter interpreter = definition.interpreterFactory().get();
            Thread reader = new Thread(() -> pump(definition.name(), process, interpreter));
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            throw new IllegalStateException("failed to launch scenario " + definition.name(), e);
        }
    }

    public synchronized void sendCommand(String cmd, Long intervalMs) {
        Running current = running.get();
        if (current == null) {
            return;
        }
        sendCommandTo(current, cmd, intervalMs);
    }

    public synchronized void stop() {
        Running current = running.getAndSet(null);
        if (current == null) {
            return;
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

    private void pump(String scenarioName, Process process, ScenarioInterpreter interpreter) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(scenarioName, interpreter, line);
            }
        } catch (IOException ignored) {
            // stop()이 프로세스를 강제 종료하면 스트림이 그냥 끊긴다 - 정상 종료 경로다.
        }
    }

    private void handleLine(String scenarioName, ScenarioInterpreter interpreter, String line) {
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
            case "stdout" -> eventPublisher.publishEvent(new ScenarioStdoutReceived(
                    scenarioName, node.path("stream").asText(), node.path("line").asText()));
            case "exited" -> {
                eventPublisher.publishEvent(new ScenarioExited(scenarioName, node.path("totalHits").asInt()));
                running.set(null);
            }
            default -> { }
        }
    }

    private record Running(Process process, PrintWriter stdin) {
    }
}
