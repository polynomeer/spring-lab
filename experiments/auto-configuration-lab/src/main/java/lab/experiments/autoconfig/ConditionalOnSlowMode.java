package lab.experiments.autoconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

// @ConditionalOnClass/@ConditionalOnProperty와 같은 패턴 - @Conditional(구현체)을 그대로
// 노출하지 않고, 의미가 드러나는 이름의 애노테이션으로 한 번 감싼다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnSlowModeCondition.class)
public @interface ConditionalOnSlowMode {
}
