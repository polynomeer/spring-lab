package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// SpringBootCondition을 상속하면 ConditionOutcome(match 여부 + 이유 메시지)을 돌려주는 것만
// 신경 쓰면 되고, 그 메시지를 ConditionEvaluationReport에 기록하는 일은 상위 클래스가 대신한다
// - @ConditionalOnClass/@ConditionalOnProperty 같은 표준 조건들과 똑같은 방식이다.
public class OnSlowModeCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String property = context.getEnvironment().getProperty("lab.slow-mode");
        ConditionMessage.Builder message = ConditionMessage.forCondition("OnSlowMode");
        if ("true".equals(property)) {
            return ConditionOutcome.match(message.foundExactly("lab.slow-mode=true"));
        }
        return ConditionOutcome.noMatch(message.didNotFind("lab.slow-mode=true property").atAll());
    }
}
