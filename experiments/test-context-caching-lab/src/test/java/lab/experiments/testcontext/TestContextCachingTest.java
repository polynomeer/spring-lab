package lab.experiments.testcontext;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import lab.experiments.testcontext.probes.ConfigXProbe;
import lab.experiments.testcontext.probes.ConfigYProbe;
import lab.experiments.testcontext.probes.DirtiesScenarioProbeA;
import lab.experiments.testcontext.probes.DirtiesScenarioProbeB;
import lab.experiments.testcontext.probes.DirtiesScenarioProbeDirty;
import lab.experiments.testcontext.probes.SharedOnceProbeA;
import lab.experiments.testcontext.probes.SharedOnceProbeB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

// SpringExtension이 관리하는 ApplicationContext는 정적(static)이고 JVM 전체에서 공유되는
// DefaultContextCache(spring-test 소스로 확인, 12번 절 - 여기선 docs 문서 쪽에 정리)에 저장된다
// - "probe" 테스트 클래스를 이 테스트 프로세스와 별도로 격리해서 실행할 수 없으므로, 각
// 시나리오는 서로 다른 @Configuration 클래스를 써서 캐시 엔트리가 절대 겹치지 않게 했다
// (실행 순서에 의존하지 않기 위함).
class TestContextCachingTest {

    @BeforeEach
    void resetCounter() {
        ContextCreationCounter.reset();
    }

    @Test
    void contextIsSharedAcrossTwoTestClassesWithIdenticalConfiguration() {
        runAndAssertNoFailures(SharedOnceProbeA.class, SharedOnceProbeB.class);

        // ContextCache는 MergedContextConfiguration을 키로 get-or-create한다 - 서로 다른
        // TestContextManager(=서로 다른 테스트 클래스)를 갖고도, 같은 설정(SharedOnceConfig)을
        // 쓰는 두 클래스는 ApplicationContext를 딱 하나만 만든다.
        assertThat(ContextCreationCounter.count()).isEqualTo(1);
    }

    @Test
    void differentConfigurationClassesGetSeparateContexts() {
        runAndAssertNoFailures(ConfigXProbe.class, ConfigYProbe.class);

        // ConfigX/ConfigY는 구조가 완전히 같지만 클래스 자체가 다르다 - 캐시 키
        // (MergedContextConfiguration)가 그 클래스 배열을 포함하므로, 두 컨텍스트는
        // 별도로 만들어진다.
        assertThat(ContextCreationCounter.count()).isEqualTo(2);
    }

    @Test
    void dirtiesContextEvictsTheCacheEntrySoTheNextIdenticalConfigurationRebuilds() {
        runAndAssertNoFailures(DirtiesScenarioProbeA.class);
        assertThat(ContextCreationCounter.count()).isEqualTo(1);

        // 같은 설정 + 클래스 레벨 @DirtiesContext(기본 AFTER_CLASS) - 실행 자체는 방금 만든
        // 컨텍스트를 그대로 재사용하지만(캐시 히트), 클래스가 끝나면
        // DirtiesContextTestExecutionListener#afterTestClass()가 그 캐시 엔트리를 close +
        // evict한다.
        runAndAssertNoFailures(DirtiesScenarioProbeDirty.class);
        assertThat(ContextCreationCounter.count()).isEqualTo(1);

        // 같은 설정으로 또 실행하지만, 캐시가 방금 폐기됐으므로 이번엔 캐시 미스 - 새
        // ApplicationContext가 만들어진다.
        runAndAssertNoFailures(DirtiesScenarioProbeB.class);
        assertThat(ContextCreationCounter.count()).isEqualTo(2);
    }

    private void runAndAssertNoFailures(Class<?>... testClasses) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(Arrays.stream(testClasses)
                        .map(testClass -> selectClass(testClass))
                        .toArray(org.junit.platform.engine.DiscoverySelector[]::new))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        assertThat(summary.getTotalFailureCount())
                .as("probe test class(es) %s failed: %s", Arrays.toString(testClasses), summary.getFailures())
                .isZero();
    }
}
