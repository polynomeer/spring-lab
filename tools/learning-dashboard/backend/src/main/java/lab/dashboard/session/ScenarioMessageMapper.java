package lab.dashboard.session;

import java.util.HashMap;
import java.util.Map;

/**
 * 도메인 이벤트(ScenarioSession이 발행하는 것들)를 {@code /topic/scenario}로 나가는 것과
 * 정확히 같은 모양의 JSON 봉투로 바꾼다. {@code lab.dashboard.web.ScenarioWebSocketController}
 * (실시간 STOMP 중계)와 {@link ScenarioRunRecorder}(DB 기록, docs/plan/04-dynamic-scenario-design.md
 * 7번 절 "실행 히스토리 스냅샷")가 이 하나의 매핑을 공유한다 - 두 곳에서 각자
 * {@code Map.of(...)}를 따로 짜면 조용히 어긋날 수 있다(예: 필드 하나를 한쪽에서만
 * 고치는 실수). 매핑이 하나뿐이면, 기록해 둔 과거 실행을 재생했을 때 프론트엔드의
 * {@code RawEventLog}/{@code SemanticEventLog}가 실시간 스트림과 똑같은 모양의 데이터로
 * 받는다는 것도 자동으로 보장된다 - 별도의 "재생용" 프론트엔드 코드가 필요 없는 이유다.
 */
public final class ScenarioMessageMapper {

    private ScenarioMessageMapper() {
    }

    public static Map<String, Object> forHit(RawHitReceived event) {
        return Map.of("type", "hit", "scenario", event.scenarioName(), "event", event.traceEvent());
    }

    public static Map<String, Object> forSemantic(SemanticEventReceived event) {
        return Map.of("type", "semantic", "scenario", event.scenarioName(), "event", event.semanticEvent());
    }

    public static Map<String, Object> forStdout(ScenarioStdoutReceived event) {
        return Map.of("type", "stdout", "scenario", event.scenarioName(), "stream", event.stream(), "line", event.line());
    }

    public static Map<String, Object> forExited(ScenarioExited event) {
        return Map.of("type", "exited", "scenario", event.scenarioName(), "totalHits", event.totalHits());
    }

    public static Map<String, Object> forHttpResponse(ScenarioHttpResponseReceived event) {
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
        return payload;
    }
}
