package lab.dashboard.conditionreport;

import lab.dashboard.session.ClasspathResolver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// ClasspathResolver(gradlew 셸아웃)를 실제로 거친다 - ConditionReportLab이 필요로 하는
// spring-boot-autoconfigure 등은 이 백엔드 모듈 자신의 클래스패스에 없어서(의도적으로 격리돼
// 있다), ScenarioSessionTest의 SampleTarget 트릭을 쓸 수 없다.
//
// ClasspathResolver()의 기본 생성자는 user.dir을 저장소 루트로 가정한다(실제 백엔드
// 애플리케이션 실행 시엔 참이다) - 하지만 Gradle이 이 테스트를 돌릴 때는 user.dir이 이
// 서브모듈 디렉터리다. 생성 직후엔 필요 없어질 값이라, 생성하는 그 순간만 user.dir을
// 저장소 루트로 바꿔치기했다가 되돌린다.
class ConditionReportRunnerTest {

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private final ConditionReportRunner runner = new ConditionReportRunner(classpathResolverAtRepoRoot(), events::add);

    private static ClasspathResolver classpathResolverAtRepoRoot() {
        String original = System.getProperty("user.dir");
        System.setProperty("user.dir", findRepoRoot().toString());
        try {
            return new ClasspathResolver();
        } finally {
            System.setProperty("user.dir", original);
        }
    }

    private static Path findRepoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("could not locate repo root (no settings.gradle.kts found upward)");
        }
        return dir;
    }

    @Test
    void defaultRunReportsGreetingMatchedAndSlowModeNotMatched() throws Exception {
        runner.run(Map.of());
        ConditionReportReceived event = awaitEvent(Duration.ofSeconds(30));

        assertThat(event.error()).isNull();
        assertThat(event.reportJson())
                .contains("\"lab.experiments.autoconfig.GreetingAutoConfiguration\":{\"fullMatch\":true")
                .contains("\"lab.experiments.autoconfig.SlowModeAutoConfiguration\":{\"fullMatch\":false");
    }

    @Test
    void propertyOverridesFlipTheReportedOutcomes() throws Exception {
        runner.run(Map.of("greeting.enabled", "false", "lab.slow-mode", "true"));
        ConditionReportReceived event = awaitEvent(Duration.ofSeconds(30));

        assertThat(event.error()).isNull();
        assertThat(event.reportJson())
                .contains("\"lab.experiments.autoconfig.GreetingAutoConfiguration\":{\"fullMatch\":false")
                .contains("\"lab.experiments.autoconfig.SlowModeAutoConfiguration\":{\"fullMatch\":true");
    }

    private ConditionReportReceived awaitEvent(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            var found = events.stream().filter(ConditionReportReceived.class::isInstance).findFirst();
            if (found.isPresent()) {
                return (ConditionReportReceived) found.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for ConditionReportReceived");
    }
}
