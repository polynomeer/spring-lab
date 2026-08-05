package lab.dashboard.session;

import lab.dashboard.interpret.SemanticEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// ClasspathResolver(gradlew 셸아웃)를 거치지 않고, tools/jdi-tracer의 SampleTarget 픽스처를
// 이 테스트 자신의 클래스패스로 직접 가리키는 ScenarioDefinition을 만들어 빠르게 검증한다 -
// gradlew 재호출 없이 ScenarioSession의 프로세스 관리/파싱/이벤트 발행 로직 자체를 확인하는
// 것이 이 테스트의 목적이다(실제 Gradle 셸아웃 경로는 수동으로 별도 확인했다).
class ScenarioSessionTest {

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private final ScenarioSession session = new ScenarioSession(events::add);

    @AfterEach
    void tearDown() {
        session.stop();
    }

    @Test
    void publishesHitsSemanticEventsAndExitedWhilePlayingThroughAFixtureScenario() throws Exception {
        ScenarioDefinition definition = new ScenarioDefinition(
                "fixture",
                System.getProperty("java.class.path"),
                "lab.tools.jdi.fixtures.SampleTarget",
                "lab.tools.jdi.fixtures.SampleTarget#greet",
                () -> hit -> List.of(SemanticEvent.of("FIXTURE_HIT", hit.hitId())));

        session.start(definition);
        awaitEventOfType(RawHitReceived.class, Duration.ofSeconds(15));

        session.sendCommand("play", 50L);
        awaitEventOfType(ScenarioExited.class, Duration.ofSeconds(15));

        assertThat(events.stream().filter(RawHitReceived.class::isInstance)).hasSize(2);
        assertThat(events.stream().filter(SemanticEventReceived.class::isInstance)).hasSize(2);

        ScenarioExited exited = events.stream()
                .filter(ScenarioExited.class::isInstance)
                .map(ScenarioExited.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(exited.totalHits()).isEqualTo(2);
        assertThat(exited.scenarioName()).isEqualTo("fixture");
    }

    private void awaitEventOfType(Class<?> type, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (events.stream().anyMatch(type::isInstance)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for event of type " + type);
    }
}
