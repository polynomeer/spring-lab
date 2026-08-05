package lab.dashboard.web;

import lab.dashboard.session.RawHitReceived;
import lab.dashboard.session.ScenarioCatalog;
import lab.dashboard.session.ScenarioExited;
import lab.dashboard.session.ScenarioSession;
import lab.dashboard.session.ScenarioStdoutReceived;
import lab.dashboard.session.SemanticEventReceived;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * ScenarioSession(프로세스 관리 + Spring 이벤트 발행)과 브라우저 사이의 얇은 STOMP 전송
 * 계층. 이 컨트롤러 자신은 "무엇이 일어났는지"를 전혀 모른다 - {@link ScenarioSession}이
 * 발행한 Spring 애플리케이션 이벤트를 구독해서 그대로 {@code /topic/scenario}로
 * 중계할 뿐이다.
 */
@Controller
public class ScenarioWebSocketController {

    private final ScenarioCatalog catalog;
    private final ScenarioSession session;
    private final SimpMessagingTemplate messagingTemplate;

    public ScenarioWebSocketController(ScenarioCatalog catalog, ScenarioSession session,
                                        SimpMessagingTemplate messagingTemplate) {
        this.catalog = catalog;
        this.session = session;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/scenario/{name}/start")
    public void start(@DestinationVariable String name) {
        catalog.resolve(name).ifPresentOrElse(session::start, () -> messagingTemplate.convertAndSend(
                "/topic/scenario", Map.of("type", "error", "message", "unknown scenario: " + name)));
    }

    @MessageMapping("/scenario/command")
    public void command(ScenarioCommandRequest request) {
        session.sendCommand(request.cmd(), request.intervalMs());
    }

    @EventListener
    public void onRawHit(RawHitReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario",
                Map.of("type", "hit", "scenario", event.scenarioName(), "event", event.traceEvent()));
    }

    @EventListener
    public void onSemanticEvent(SemanticEventReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario",
                Map.of("type", "semantic", "scenario", event.scenarioName(), "event", event.semanticEvent()));
    }

    @EventListener
    public void onStdout(ScenarioStdoutReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario",
                Map.of("type", "stdout", "scenario", event.scenarioName(), "stream", event.stream(), "line", event.line()));
    }

    @EventListener
    public void onExited(ScenarioExited event) {
        messagingTemplate.convertAndSend("/topic/scenario",
                Map.of("type", "exited", "scenario", event.scenarioName(), "totalHits", event.totalHits()));
    }
}
