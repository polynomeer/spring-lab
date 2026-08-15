package lab.experiments.importselector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RepeatedGreetingRegistrar.class)
public @interface EnableRepeatedGreeting {

    int repeatCount() default 1;

    String message() default "Hi";
}
