package lab.experiments.configphase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

// PlainConditionalFallbackConfig와 판정 로직 자체는 같지만, 조건이 ConfigurationCondition을
// 구현해 REGISTER_BEAN 단계만 선언한다. ConditionEvaluator#shouldSkip은 조건의
// requiredPhase가 현재 확인 중인 phase와 일치할 때만 condition.matches()를 호출하므로,
// PARSE_CONFIGURATION 체크(널 phase의 기본 해석) 시점에는 이 조건이 아예 평가되지 않고
// 항상 "건너뛰지 않음"으로 취급된다 - 실제로 건너뛸지는 오직 REGISTER_BEAN 단계(자신의
// @Bean 메서드를 로드하기 직전)에서만 결정된다.
@Configuration
@Conditional(PhasedOnMissingBeanCondition.class)
public class PhasedConditionalFallbackConfig {

    @Bean
    public SharedValue shared() {
        return new SharedValue("from-phased-fallback");
    }
}
