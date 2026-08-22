package lab.experiments.configphase;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// 평범한 Condition이다 - ConfigurationCondition을 구현하지 않았으므로 "언제(어느 단계)
// 평가할지"를 스스로 선언할 방법이 없다. 클래스 레벨에 붙이면 ConditionEvaluator가
// PARSE_CONFIGURATION 단계로 기본 판정한다.
public class PlainOnMissingBeanCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !context.getRegistry().containsBeanDefinition("shared");
    }
}
