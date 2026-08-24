package lab.experiments.composedannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

// @Component를 메타 애노테이션으로 붙였다 - @Loggable을 직접 붙인 클래스는
// @Component를 직접 붙인 것과 똑같이 컴포넌트 스캔에 걸려야 한다("합성 애노테이션").
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Loggable {
}
