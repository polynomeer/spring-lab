package lab.dashboard.session;

import java.util.List;
import java.util.Map;

import lab.dashboard.interpret.SemanticEvent;
import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// ScenarioWebSocketController(실시간 STOMP 중계)와 ScenarioRunRecorder(DB 기록)가 이 매핑을
// 공유한다 - 어느 한쪽이 필드를 빠뜨리면 재생 화면(RunHistoryModal)이 실시간 화면과 다른
// 모양의 데이터를 받게 되므로, 각 변환이 정확히 어떤 키를 채우는지 여기서 고정해 둔다.
class ScenarioMessageMapperTest {

    private static final TraceEvent SAMPLE_EVENT = new TraceEvent(
            1, 0L, "main",
            new TraceEvent.Location("lab.Foo", "bar", 10),
            List.of(), List.of(), false);

    @Test
    void mapsAHitToItsEnvelope() {
        Map<String, Object> payload = ScenarioMessageMapper.forHit(new RawHitReceived("bean-lifecycle", SAMPLE_EVENT));

        assertThat(payload)
                .containsEntry("type", "hit")
                .containsEntry("scenario", "bean-lifecycle")
                .containsEntry("event", SAMPLE_EVENT);
    }

    @Test
    void mapsASemanticEventToItsEnvelope() {
        SemanticEvent event = SemanticEvent.of("SINGLETON_FACTORY_REGISTERED", 1, "beanName", "aopConfig");

        Map<String, Object> payload = ScenarioMessageMapper.forSemantic(new SemanticEventReceived("bean-lifecycle", event));

        assertThat(payload)
                .containsEntry("type", "semantic")
                .containsEntry("scenario", "bean-lifecycle")
                .containsEntry("event", event);
    }

    @Test
    void mapsStdoutToItsEnvelope() {
        Map<String, Object> payload = ScenarioMessageMapper.forStdout(
                new ScenarioStdoutReceived("bean-lifecycle", "stdout", "hello"));

        assertThat(payload)
                .containsEntry("type", "stdout")
                .containsEntry("scenario", "bean-lifecycle")
                .containsEntry("stream", "stdout")
                .containsEntry("line", "hello");
    }

    @Test
    void mapsExitedToItsEnvelope() {
        Map<String, Object> payload = ScenarioMessageMapper.forExited(new ScenarioExited("bean-lifecycle", 12));

        assertThat(payload)
                .containsEntry("type", "exited")
                .containsEntry("scenario", "bean-lifecycle")
                .containsEntry("totalHits", 12);
    }

    @Test
    void mapsASuccessfulHttpResponseWithoutAnErrorKey() {
        Map<String, Object> payload = ScenarioMessageMapper.forHttpResponse(
                new ScenarioHttpResponseReceived("dispatcher-flow", "GET", "/users/42", 200, null));

        assertThat(payload)
                .containsEntry("type", "httpResponse")
                .containsEntry("scenario", "dispatcher-flow")
                .containsEntry("method", "GET")
                .containsEntry("path", "/users/42")
                .containsEntry("status", 200)
                .doesNotContainKey("error");
    }

    @Test
    void mapsAFailedHttpResponseWithoutAStatusKey() {
        Map<String, Object> payload = ScenarioMessageMapper.forHttpResponse(
                new ScenarioHttpResponseReceived("dispatcher-flow", "GET", "/users/boom", null, "timeout"));

        assertThat(payload)
                .containsEntry("type", "httpResponse")
                .containsEntry("error", "timeout")
                .doesNotContainKey("status");
    }
}
