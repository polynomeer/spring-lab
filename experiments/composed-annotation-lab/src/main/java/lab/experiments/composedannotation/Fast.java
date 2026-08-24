package lab.experiments.composedannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AliasFor;

// @Qualifier를 메타 애노테이션으로 붙이고, 이 애노테이션 자신의 value 속성을
// @AliasFor로 @Qualifier.value()에 "연결"해 뒀다 - @Fast("turbo")를 붙이면
// @Qualifier("turbo")를 붙인 것과 정확히 같은 값으로 취급돼야 한다.
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Fast {

    @AliasFor(annotation = Qualifier.class, attribute = "value")
    String value() default "fast-lane";
}
