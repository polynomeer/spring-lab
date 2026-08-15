package lab.experiments.importselector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

// @EnableXxx류 애노테이션의 전형적인 모양 - 실제 등록 로직은 GreetingImportSelector가 담당하고,
// 이 애노테이션은 그 셀렉터가 읽을 파라미터(languages)만 들고 있다.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(GreetingImportSelector.class)
public @interface EnableGreeting {

    String[] languages() default {};
}
