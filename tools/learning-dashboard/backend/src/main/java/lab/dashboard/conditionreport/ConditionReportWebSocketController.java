package lab.dashboard.conditionreport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * condition-report 시나리오 전용 STOMP 경로 - {@code /topic/scenario}(스텝 실행형 시나리오들의
 * {@link lab.dashboard.web.ScenarioWebSocketController})와 완전히 분리한다. 이 시나리오의
 * 메시지 모양(전체 리포트 트리 하나)이 {@code ScenarioMessage} 유니언의 다른 멤버들과
 * 이질적이라, 같은 토픽에 억지로 섞지 않는다.
 */
@Controller
public class ConditionReportWebSocketController {

    private final ConditionReportRunner runner;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConditionReportWebSocketController(ConditionReportRunner runner, SimpMessagingTemplate messagingTemplate) {
        this.runner = runner;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/condition-report/run")
    public void run(ConditionReportRunRequest request) {
        runner.run(request.overrides() != null ? request.overrides() : Map.of());
    }

    @EventListener
    public void onReport(ConditionReportReceived event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("overrides", event.propertyOverrides());
        if (event.reportJson() != null) {
            payload.put("report", parseOrNull(event.reportJson()));
        }
        if (event.error() != null) {
            payload.put("error", event.error());
        }
        messagingTemplate.convertAndSend("/topic/condition-report", payload);
    }

    private JsonNode parseOrNull(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
