package lab.dashboard.scenario;

import java.util.List;
import java.util.Map;

import lab.dashboard.interpret.SemanticEvent;
import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineLabelInterpreterTest {

    private final InlineLabelInterpreter interpreter =
            new InlineLabelInterpreter(Map.of("main", "빈 생성 시작"));

    @Test
    void emitsALabeledSemanticEventWhenTheHitIsOnALabeledMethod() {
        TraceEvent hit = new TraceEvent(
                7, 0L, "main", new TraceEvent.Location("lab.dynamic.MyLab", "main", 4),
                List.of(), List.of(), true);

        List<SemanticEvent> events = interpreter.onHit(hit);

        assertThat(events).containsExactly(
                new SemanticEvent("빈 생성 시작", 7, Map.of("method", "main")));
    }

    @Test
    void emitsNothingWhenTheHitIsOnAnUnlabeledMethod() {
        TraceEvent hit = new TraceEvent(
                1, 0L, "main", new TraceEvent.Location("lab.dynamic.MyLab", "helper", 10),
                List.of(), List.of(), true);

        assertThat(interpreter.onHit(hit)).isEmpty();
    }
}
