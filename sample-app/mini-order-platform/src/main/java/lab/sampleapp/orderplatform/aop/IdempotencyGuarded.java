package lab.sampleapp.orderplatform.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애노테이션이 붙은 메서드는 첫 번째 파라미터를 멱등성 키로 취급한다(관례 - IdempotencyAspect
 * 참고). 같은 키로 두 번째 호출하면 실제 메서드를 다시 실행하지 않고 첫 호출의 결과를 그대로
 * 반환한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdempotencyGuarded {
}
