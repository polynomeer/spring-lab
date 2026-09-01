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
                () -> hit -> List.of(SemanticEvent.of("FIXTURE_HIT", hit.hitId())), false);

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

    // docs/plan/04-dynamic-scenario-design.md 3번 절 - breakpointSpec이 이제 spec 파일 경로가
    // 아니라 인라인 텍스트다(여러 줄일 수 있음). 주석/빈 줄이 섞인 여러 줄짜리 스펙을 넣어도
    // 위 단일 줄 테스트와 똑같이 히트 2개(SampleTarget#greet 2회 호출)가 나와야 한다 - 각
    // 줄이 별도 인자로 풀려서 TracerServer에 전달된다는 것과, 빈 줄은 걸러진다는 것을 함께
    // 확인한다.
    @Test
    void splitsAMultiLineInlineBreakpointSpecIntoSeparateArgumentsJustLikeTheSpecFilesUsedTo() throws Exception {
        ScenarioDefinition definition = new ScenarioDefinition(
                "fixture-multiline",
                System.getProperty("java.class.path"),
                "lab.tools.jdi.fixtures.SampleTarget",
                "# a comment line, ignored by JdiSupport\n\nlab.tools.jdi.fixtures.SampleTarget#greet",
                () -> hit -> List.of(SemanticEvent.of("FIXTURE_HIT", hit.hitId())), false);

        session.start(definition);
        awaitEventOfType(RawHitReceived.class, Duration.ofSeconds(15));

        session.sendCommand("play", 50L);
        awaitEventOfType(ScenarioExited.class, Duration.ofSeconds(15));

        assertThat(events.stream().filter(RawHitReceived.class::isInstance)).hasSize(2);
    }

    // docs/plan/04-dynamic-scenario-design.md 5번 절 - dynamicallyCompiled=true인 시나리오만
    // 타임아웃 대상이라는 것과, 실제로 무한루프를 넣으면 그 타임아웃이 정말로 프로세스를
    // 강제 종료하는지를 함께 확인한다. 실제 몇 분을 기다리지 않도록 짧은 타임아웃을 주는
    // package-private 생성자를 쓴다.
    @Test
    void dynamicallyCompiledScenarioThatNeverExitsIsKilledByTheTimeout() throws Exception {
        ScenarioSession shortTimeoutSession = new ScenarioSession(events::add, Duration.ofSeconds(1));
        try {
            ScenarioDefinition definition = new ScenarioDefinition(
                    "fixture-infinite",
                    System.getProperty("java.class.path"),
                    "lab.tools.jdi.fixtures.InfiniteLoopTarget",
                    "lab.tools.jdi.fixtures.InfiniteLoopTarget#tick",
                    () -> hit -> List.of(), true);

            shortTimeoutSession.start(definition);
            awaitEventOfType(RawHitReceived.class, Duration.ofSeconds(15));

            awaitEventOfType(ScenarioTimedOut.class, Duration.ofSeconds(15));

            ScenarioTimedOut timedOut = events.stream()
                    .filter(ScenarioTimedOut.class::isInstance)
                    .map(ScenarioTimedOut.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(timedOut.scenarioName()).isEqualTo("fixture-infinite");

            // ScenarioExited가 절대 안 온다는 것도 확인한다 - 자연 종료가 아니라 강제
            // 종료라는 뜻이다(무한루프이므로 스스로는 절대 끝나지 않는다).
            assertThat(events.stream().anyMatch(ScenarioExited.class::isInstance)).isFalse();
        } finally {
            shortTimeoutSession.stop();
        }
    }

    // docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행" - startComparison은
    // start()와 달리 서로를 정리하지 않고 나란히 떠 있어야 한다는 것과, 시작하자마자 별도의
    // play 명령 없이도 바로 재생이 진행된다는 것을 함께 확인한다.
    @Test
    void startComparisonRunsTwoNamedScenariosConcurrentlyWithoutStoppingEachOther() throws Exception {
        ScenarioDefinition a = new ScenarioDefinition(
                "comparison-a", System.getProperty("java.class.path"),
                "lab.tools.jdi.fixtures.SampleTarget", "lab.tools.jdi.fixtures.SampleTarget#greet",
                () -> hit -> List.of(), false);
        ScenarioDefinition b = new ScenarioDefinition(
                "comparison-b", System.getProperty("java.class.path"),
                "lab.tools.jdi.fixtures.SampleTarget", "lab.tools.jdi.fixtures.SampleTarget#greet",
                () -> hit -> List.of(), false);

        session.startComparison(a, 50L);
        session.startComparison(b, 50L);

        awaitCondition(Duration.ofSeconds(15), () -> events.stream()
                .filter(ScenarioExited.class::isInstance).map(ScenarioExited.class::cast)
                .map(ScenarioExited::scenarioName)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(List.of("comparison-a", "comparison-b")));

        long hitsForA = events.stream().filter(RawHitReceived.class::isInstance)
                .map(RawHitReceived.class::cast).filter(e -> e.scenarioName().equals("comparison-a")).count();
        long hitsForB = events.stream().filter(RawHitReceived.class::isInstance)
                .map(RawHitReceived.class::cast).filter(e -> e.scenarioName().equals("comparison-b")).count();
        assertThat(hitsForA).isEqualTo(2);
        assertThat(hitsForB).isEqualTo(2);
    }

    // docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행" - 실제로 겪었던 회귀
    // 버그를 그대로 재현해서 고정한다: 비교 화면에서 고른 시나리오 이름이 메인 화면에서
    // 지금 재생 중인 시나리오 이름과 우연히 겹치면, startComparison이 메인 세션을 조용히
    // 죽이고 자기 걸로 갈아치웠었다(처음에는 메인 세션과 비교 세션을 이름만으로 구분되는
    // 같은 맵에 뒀기 때문).
    //
    // 두 세션이 같은 이름을 쓰므로 이벤트만으로는 "어느 쪽에서 왔는지" 구분할 수 없다 -
    // 그래서 순서를 이용해 결정론적으로 검증한다: 메인 세션은 첫 히트에서 멈춰 둔 채(아직
    // step/play를 보내지 않음) 비교 세션만 끝까지 돌려 자연 종료(ScenarioExited)시킨다.
    // 그 시점에 비교 세션의 Running은 이미 스스로 정리됐으니, 그 뒤에 메인 세션에 step을
    // 보내 새 히트가 나오는지만 보면 된다 - 버그가 있었다면 startComparison 시점에 메인
    // 세션도 함께 죽었을 것이므로 이 step은 아무것도 못 깨우고 타임아웃난다.
    @Test
    void startingAComparisonWithTheSameNameAsTheMainSessionDoesNotStopTheMainSession() throws Exception {
        ScenarioDefinition definition = new ScenarioDefinition(
                "shared-name", System.getProperty("java.class.path"),
                "lab.tools.jdi.fixtures.SampleTarget", "lab.tools.jdi.fixtures.SampleTarget#greet",
                () -> hit -> List.of(), false);

        session.start(definition);
        awaitEventOfType(RawHitReceived.class, Duration.ofSeconds(15));

        session.startComparison(definition, 50L);
        awaitEventOfType(ScenarioExited.class, Duration.ofSeconds(15));

        long hitsBeforeMainStep = events.stream().filter(RawHitReceived.class::isInstance).count();
        session.sendCommand("step", null);
        awaitCondition(Duration.ofSeconds(15), () -> events.stream()
                .filter(RawHitReceived.class::isInstance).count() > hitsBeforeMainStep);
    }

    private void awaitCondition(Duration timeout, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for condition");
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
