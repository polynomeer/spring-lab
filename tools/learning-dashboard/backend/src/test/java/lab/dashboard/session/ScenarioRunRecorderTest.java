package lab.dashboard.session;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lab.dashboard.interpret.SemanticEvent;
import lab.dashboard.scenario.ScenarioRunEntity;
import lab.dashboard.scenario.ScenarioRunRepository;
import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest(리포지토리 슬라이스) - 실제 H2 인메모리 DB 위에서 ScenarioRunRepository를
// 진짜로 굴려서, JSON 직렬화까지 포함한 전체 기록 경로를 확인한다.
@DataJpaTest
class ScenarioRunRecorderTest {

    @Autowired
    private ScenarioRunRepository repository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recordsACompletedRunAsAnOrderedJsonEnvelopeArray() throws Exception {
        ScenarioRunRecorder recorder = new ScenarioRunRecorder(repository, mapper);

        recorder.onStarted(new ScenarioStarted("demo"));
        recorder.onRawHit(new RawHitReceived("demo", sampleHit(1)));
        recorder.onSemanticEvent(new SemanticEventReceived("demo", SemanticEvent.of("DEMO_EVENT", 1)));
        recorder.onExited(new ScenarioExited("demo", 1));

        List<ScenarioRunEntity> runs = repository.findByScenarioNameOrderByStartedAtDesc("demo");
        assertThat(runs).hasSize(1);

        ScenarioRunEntity run = runs.get(0);
        assertThat(run.getTotalHits()).isEqualTo(1);
        assertThat(run.isTimedOut()).isFalse();

        JsonNode events = mapper.readTree(run.getEventsJson());
        assertThat(events).hasSize(3);
        assertThat(events.get(0).get("type").asText()).isEqualTo("hit");
        assertThat(events.get(1).get("type").asText()).isEqualTo("semantic");
        assertThat(events.get(1).get("event").get("type").asText()).isEqualTo("DEMO_EVENT");
        assertThat(events.get(2).get("type").asText()).isEqualTo("exited");
    }

    @Test
    void recordsATimedOutRunWithNullTotalHits() {
        ScenarioRunRecorder recorder = new ScenarioRunRecorder(repository, mapper);

        recorder.onStarted(new ScenarioStarted("infinite"));
        recorder.onRawHit(new RawHitReceived("infinite", sampleHit(1)));
        recorder.onTimedOut(new ScenarioTimedOut("infinite", 120));

        List<ScenarioRunEntity> runs = repository.findByScenarioNameOrderByStartedAtDesc("infinite");
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getTotalHits()).isNull();
        assertThat(runs.get(0).isTimedOut()).isTrue();
    }

    @Test
    void discardsARunThatNeverFinishedBeforeBeingReplacedByAnother() {
        ScenarioRunRecorder recorder = new ScenarioRunRecorder(repository, mapper);

        recorder.onStarted(new ScenarioStarted("abandoned"));
        recorder.onRawHit(new RawHitReceived("abandoned", sampleHit(1)));
        // 완료/타임아웃 이벤트 없이 곧바로 다른 시나리오가 시작됨 - "abandoned"는 기록되지 않는다.
        recorder.onStarted(new ScenarioStarted("replacement"));
        recorder.onExited(new ScenarioExited("replacement", 0));

        assertThat(repository.findByScenarioNameOrderByStartedAtDesc("abandoned")).isEmpty();
        assertThat(repository.findByScenarioNameOrderByStartedAtDesc("replacement")).hasSize(1);
    }

    // docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행" - 서로 다른 이름의 두
    // 실행이 동시에 진행 중일 때(A의 ScenarioStarted 이후, A가 끝나기 전에 B가 시작), 둘 다
    // 온전히 따로 기록돼야 한다 - Map으로 일반화하기 전에는 B의 ScenarioStarted가 A의 기록
    // 버퍼를 조용히 밀어내 A의 히트/종료가 아예 기록되지 않는 버그가 있었다.
    @Test
    void recordsTwoConcurrentlyStartedScenariosIndependently() {
        ScenarioRunRecorder recorder = new ScenarioRunRecorder(repository, mapper);

        recorder.onStarted(new ScenarioStarted("comparison-a"));
        recorder.onStarted(new ScenarioStarted("comparison-b"));
        recorder.onRawHit(new RawHitReceived("comparison-a", sampleHit(1)));
        recorder.onRawHit(new RawHitReceived("comparison-b", sampleHit(1)));
        recorder.onExited(new ScenarioExited("comparison-b", 1));
        recorder.onRawHit(new RawHitReceived("comparison-a", sampleHit(2)));
        recorder.onExited(new ScenarioExited("comparison-a", 2));

        List<ScenarioRunEntity> runsA = repository.findByScenarioNameOrderByStartedAtDesc("comparison-a");
        List<ScenarioRunEntity> runsB = repository.findByScenarioNameOrderByStartedAtDesc("comparison-b");
        assertThat(runsA).hasSize(1);
        assertThat(runsB).hasSize(1);
        assertThat(runsA.get(0).getTotalHits()).isEqualTo(2);
        assertThat(runsB.get(0).getTotalHits()).isEqualTo(1);
    }

    private TraceEvent sampleHit(int hitId) {
        return new TraceEvent(hitId, 0L, "main",
                new TraceEvent.Location("lab.dynamic.Demo", "main", 10), List.of(), List.of(), true);
    }
}
