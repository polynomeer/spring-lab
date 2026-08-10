package lab.sampleapp.orderplatform.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Audited(@Order(2))보다 안쪽, Idempotency/Retry(@Order(4)/(5))보다 바깥쪽에 둔다 - 권한이
 * 없으면 캐시 조회나 재시도 같은 비용을 전혀 들이지 않고 즉시 실패해야 하기 때문이다. 대신
 * 그 거부 자체는 Audited가 감사 로그에 남긴다(바깥쪽이므로 이 예외를 그대로 통과시킨다).
 */
@Aspect
@Component
@Order(3)
public class AuthorizationAspect {

    private final CurrentActor currentActor;

    public AuthorizationAspect(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @Around("@annotation(lab.sampleapp.orderplatform.aop.RequiresRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        RequiresRole requiresRole = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RequiresRole.class);
        Role actualRole = currentActor.get().role();
        if (actualRole != requiresRole.value()) {
            throw new AccessDeniedException(
                    "requires role " + requiresRole.value() + " but actor has " + actualRole);
        }
        return joinPoint.proceed();
    }
}
