package lab.dashboard.web;

import lab.dashboard.session.RawHitReceived;
import lab.dashboard.session.ScenarioCatalog;
import lab.dashboard.session.ScenarioExited;
import lab.dashboard.session.ScenarioHttpResponseReceived;
import lab.dashboard.session.ScenarioSession;
import lab.dashboard.session.ScenarioStdoutReceived;
import lab.dashboard.session.SemanticEventReceived;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
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

    @MessageMapping("/scenario/http-request")
    public void httpRequest(ScenarioHttpRequestCommand request) {
        session.sendHttpRequest(request.method(), request.path(), request.body());
    }

    /**
     * 이 컨트롤러의 @MessageMapping 메서드(주로 start() - classpath 해석이 실패하면
     * ScenarioCatalog#resolve()가 IllegalStateException을 던진다) 밖으로 새어 나오는 예외를
     * 전부 여기서 잡는다. 예전에는 서버 로그에만 "Unhandled exception from message handler
     * method"로 남고 브라우저에는 아무 신호도 가지 않아서, 세션이 왜 시작되지 않는지 알 방법이
     * HIT 0에서 하염없이 기다리는 것뿐이었다(직접 겪은 문제) - unknown scenario 브랜치가 이미
     * 쓰던 것과 같은 {"type":"error"} 모양으로 통일해서 프론트가 한 곳에서만 에러를 처리하면
     * 되게 했다.
     */
    @MessageExceptionHandler
    public void handleException(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        messagingTemplate.convertAndSend("/topic/scenario", Map.of("type", "error", "message", message));
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

    @EventListener
    public void onHttpResponse(ScenarioHttpResponseReceived event) {
        // status/error가 서로 배타적이라(ScenarioHttpResponseReceived 참고) Map.of는 못 쓴다 -
        // null 값을 넣을 수 없어서 HashMap으로 있는 필드만 채운다.
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "httpResponse");
        payload.put("scenario", event.scenarioName());
        payload.put("method", event.method());
        payload.put("path", event.path());
        if (event.status() != null) {
            payload.put("status", event.status());
        }
        if (event.error() != null) {
            payload.put("error", event.error());
        }
        messagingTemplate.convertAndSend("/topic/scenario", payload);
    }
}
