package lab.experiments.configphase;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

// PlainOnMissingBeanCondition과 판정 로직 자체는 완전히 같다 - 유일한 차이는
// ConfigurationCondition을 구현해서 REGISTER_BEAN 단계에서만 평가되게 명시적으로
// 선언한 것뿐이다. 실제 Spring Boot의 @ConditionalOnMissingBean도 정확히 이 인터페이스를
// 이 단계로 구현한다.
public class PhasedOnMissingBeanCondition implements ConfigurationCondition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !context.getRegistry().containsBeanDefinition("shared");
    }

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }
}
