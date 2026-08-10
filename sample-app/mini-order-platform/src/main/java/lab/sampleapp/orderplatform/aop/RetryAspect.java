package lab.sampleapp.orderplatform.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 가장 안쪽(@Order(5))에 둔다 - 실제 대상 메서드 호출에 가장 가까운 자리에서만 "일시적
 * 실패"를 재시도해야, 권한 검사 실패(AuthorizationAspect) 같은 재시도해 봐야 소용없는
 * 예외까지 불필요하게 반복하지 않는다.
 */
@Aspect
@Component
@Order(5)
public class RetryAspect {

    @Around("@annotation(lab.sampleapp.orderplatform.aop.Retryable)")
    public Object retry(ProceedingJoinPoint joinPoint) throws Throwable {
        Retryable retryable = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(Retryable.class);

        Throwable lastFailure;
        int attempt = 0;
        do {
            attempt++;
            try {
                return joinPoint.proceed();
            } catch (Throwable ex) {
                if (!retryable.retryFor().isInstance(ex)) {
                    throw ex;
                }
                lastFailure = ex;
            }
        } while (attempt < retryable.maxAttempts());

        throw lastFailure;
    }
}
