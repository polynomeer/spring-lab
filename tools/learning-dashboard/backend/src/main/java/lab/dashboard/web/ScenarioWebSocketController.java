package lab.dashboard.web;

import lab.dashboard.session.RawHitReceived;
import lab.dashboard.session.ScenarioCatalog;
import lab.dashboard.session.ScenarioExited;
import lab.dashboard.session.ScenarioHttpResponseReceived;
import lab.dashboard.session.ScenarioMessageMapper;
import lab.dashboard.session.ScenarioSession;
import lab.dashboard.session.ScenarioStdoutReceived;
import lab.dashboard.session.ScenarioTimedOut;
import lab.dashboard.session.SemanticEventReceived;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * ScenarioSession(프로세스 관리 + Spring 이벤트 발행)과 브라우저 사이의 얇은 STOMP 전송
 * 계층. 이 컨트롤러 자신은 "무엇이 일어났는지"를 전혀 모른다 - {@link ScenarioSession}이
 * 발행한 Spring 애플리케이션 이벤트를 구독해서 그대로 {@code /topic/scenario}로
 * 중계할 뿐이다. 이벤트를 JSON 봉투로 바꾸는 일 자체는 {@link ScenarioMessageMapper}에
 * 맡긴다 - {@code ScenarioRunRecorder}(실행 히스토리 기록)도 같은 매핑을 쓴다.
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
        messagingTemplate.convertAndSend("/topic/scenario", ScenarioMessageMapper.forHit(event));
    }

    @EventListener
    public void onSemanticEvent(SemanticEventReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario", ScenarioMessageMapper.forSemantic(event));
    }

    @EventListener
    public void onStdout(ScenarioStdoutReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario", ScenarioMessageMapper.forStdout(event));
    }

    @EventListener
    public void onExited(ScenarioExited event) {
        messagingTemplate.convertAndSend("/topic/scenario", ScenarioMessageMapper.forExited(event));
    }

    /**
     * 무한루프 같은 즉석 코드가 실행 타임아웃에 걸려 강제 종료됐다는 신호(5번 절 안전장치) -
     * "unknown scenario"/컴파일 실패와 같은 {@code {"type":"error"}} 모양으로 통일해서
     * 프론트가 이미 갖고 있는 에러 배너를 그대로 재사용한다.
     */
    @EventListener
    public void onTimedOut(ScenarioTimedOut event) {
        messagingTemplate.convertAndSend("/topic/scenario", Map.of("type", "error", "message",
                "실행 시간이 " + event.timeoutSeconds() + "초를 넘어 강제 종료했습니다 - 무한루프가 있는지 확인하세요."));
    }

    @EventListener
    public void onHttpResponse(ScenarioHttpResponseReceived event) {
        messagingTemplate.convertAndSend("/topic/scenario", ScenarioMessageMapper.forHttpResponse(event));
    }
}
