package lab.experiments.configphase;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.experiments.configphase.scanned.ScannedComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationConditionPhaseTest {

    /**
     * SharedBeanProviderConfig가 먼저 등록/파싱/로드된다. PlainConditionalFallbackConfig의
     * PARSE_CONFIGURATION 체크 시점에는 아직 어떤 @Bean 메서드도 실제 BeanDefinition으로
     * 등록되지 않은 상태라(파싱 전체가 끝난 뒤에야 reader가 일괄 등록한다) "shared"가
     * 없다고 판단해 통과하지만, 등록 순서상 SharedBeanProviderConfig의 로드가 먼저
     * 끝나 있으므로 REGISTER_BEAN 안전망 재평가에서는 정확히 "이미 있다"고 판단해
     * 자기 자신의 @Bean shared()만 건너뛴다.
     */
    @Test
    void providerRegisteredFirstLetsPlainConditionCorrectlySelfSkipAtRegisterTime() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                SharedBeanProviderConfig.class, PlainConditionalFallbackConfig.class)) {

            assertThat(context.getBean(SharedValue.class).source()).isEqualTo("from-provider");
        }
    }

    /**
     * 등록 순서를 뒤집으면 결과는 같아도 이유가 완전히 달라진다.
     * PlainConditionalFallbackConfig가 먼저 파싱/로드되므로, 그 시점(파싱 단계와
     * REGISTER_BEAN 안전망 단계 둘 다)에는 SharedBeanProviderConfig의 "shared"가
     * 아직 등록 전이라 조건이 계속 "없다"고 판단한다 - 그래서 fallback의
     * shared("from-plain-fallback")가 실제로 등록된다. 그런데 바로 다음으로 처리되는
     * SharedBeanProviderConfig가 조건 없이 같은 이름 "shared"를 다시 등록하면서,
     * doc45(BeanDefinition overriding)에서 확인한 "나중에 등록된 정의가 조용히
     * 이긴다"는 기본 override 규칙에 따라 그것을 덮어써 버린다. 최종 승자는 앞의
     * 테스트와 똑같이 "from-provider"이지만, "조건이 스스로 건너뜀"이 아니라
     * "조건은 통과했지만 나중에 덮어써짐"이라는 전혀 다른 경로다.
     */
    @Test
    void fallbackRegisteredFirstRegistersItsOwnBeanButProviderSilentlyOverridesIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PlainConditionalFallbackConfig.class, SharedBeanProviderConfig.class)) {

            assertThat(context.getBean(SharedValue.class).source()).isEqualTo("from-provider");
        }
    }

    /**
     * ConfigurationCondition으로 REGISTER_BEAN 단계를 명시한 조건도 이 시나리오에서는
     * 위 첫 번째 테스트와 똑같은 최종 결과로 수렴한다 - PARSE_CONFIGURATION 체크
     * 자체를 건너뛰고 곧바로 REGISTER_BEAN 안전망에서만 판정되기 때문에, "provider가
     * 먼저 로드된 뒤 판정"이라는 지점은 plain 조건과 동일하다. @Bean 메서드만 있고
     * @ComponentScan/@Import 같은 부수 효과가 없는 이 실험 설계에서는, PLAIN과
     * PHASED의 차이가 겉으로 드러나지 않는다 - 진짜 차이는 아래 @ComponentScan
     * 시나리오에서 나타난다.
     */
    @Test
    void phasedConditionAlsoConvergesToSameWinnerInThisBeanOnlyScenario() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                SharedBeanProviderConfig.class, PhasedConditionalFallbackConfig.class)) {

            assertThat(context.getBean(SharedValue.class).source()).isEqualTo("from-provider");
        }
    }

    /**
     * "shared"가 아직 없는 상태에서는 PARSE_CONFIGURATION 단계의 plain 조건이
     * 통과하므로 @ComponentScan이 실행되고 ScannedComponent가 등록된다.
     */
    @Test
    void plainConditionPassesSoComponentScanRunsAndRegistersScannedBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PlainConditionalScanConfig.class)) {

            assertThat(context.getBeanNamesForType(ScannedComponent.class)).hasSize(1);
        }
    }

    /**
     * "shared"를 @Configuration 파싱이 시작되기 전에 직접 등록해 두면,
     * PlainOnMissingBeanCondition의 PARSE_CONFIGURATION 체크 시점에도 이미 존재가
     * 확정된다 - processConfigurationClass() 맨 앞의 shouldSkip이 true를 반환해
     * @ComponentScan 자체가 아예 실행되지 않고, ScannedComponent도 등록되지 않는다.
     */
    @Test
    void plainConditionFailsAtParseTimeSoComponentScanNeverRuns() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("shared", SharedValue.class, () -> new SharedValue("from-provider"));
            context.register(PlainConditionalScanConfig.class);
            context.refresh();

            assertThat(context.getBeanNamesForType(ScannedComponent.class)).isEmpty();
        }
    }

    /**
     * REGISTER_BEAN 단계 조건은 스캔이 끝난 "뒤"에야 의미가 있는데, @ComponentScan은
     * 파싱 시점에 즉시 실행돼 버린다. 이 모순을 ConfigurationClassParser가 스스로
     * 감지해 즉시 ApplicationContextException을 던진다 - "shared" 존재 여부와
     * 무관하게, 이 조합 자체가 애초에 허용되지 않는다.
     */
    @Test
    void phasedConditionCombinedWithComponentScanIsRejectedOutright() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PhasedConditionalScanConfig.class);

            assertThatThrownBy(context::refresh)
                    .hasRootCauseInstanceOf(ApplicationContextException.class)
                    .rootCause()
                    .hasMessageContaining("could not be used with conditions in REGISTER_BEAN phase")
                    .hasMessageContaining(PhasedConditionalScanConfig.class.getName());
        }
    }
}
