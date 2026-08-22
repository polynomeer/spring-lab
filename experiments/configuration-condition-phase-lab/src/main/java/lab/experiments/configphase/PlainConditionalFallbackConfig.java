package lab.experiments.configphase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

// 클래스 레벨의 평범한 Condition은 requiredPhase가 없으므로(ConfigurationCondition
// 미구현) 두 지점에서 평가된다: (1) ConfigurationClassParser의 PARSE_CONFIGURATION
// 체크, (2) 각 설정 클래스의 빈 정의를 reader가 로드하기 직전에 다시 도는 REGISTER_BEAN
// 안전망 체크(ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator) - 이
// 조건은 requiredPhase가 null이라 "어느 phase로 확인하든" 항상 평가 대상에 포함되기
// 때문이다. 그래서 "shared"가 아직 없어 파싱 시점엔 통과하더라도, 등록 순서상 자신보다
// 먼저 처리되는 설정 클래스가 "shared"를 이미 등록해 버렸다면 이 안전망 재평가에서
// 정확히 걸러진다.
@Configuration
@Conditional(PlainOnMissingBeanCondition.class)
public class PlainConditionalFallbackConfig {

    @Bean
    public SharedValue shared() {
        return new SharedValue("from-plain-fallback");
    }
}
