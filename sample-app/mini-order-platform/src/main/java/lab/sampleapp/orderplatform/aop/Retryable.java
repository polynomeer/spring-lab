package lab.sampleapp.orderplatform.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Retryable {

    int maxAttempts() default 3;

    Class<? extends Throwable> retryFor() default TransientOperationException.class;
}
