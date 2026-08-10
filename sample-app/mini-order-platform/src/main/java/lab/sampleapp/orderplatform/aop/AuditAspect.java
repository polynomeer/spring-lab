package lab.sampleapp.orderplatform.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 성공/실패(예외) 양쪽 모두 정확히 한 번 기록한다 - 재시도(RetryAspect, @Order(5))보다
 * 바깥쪽에 있으므로 "재시도 도중의 각 시도"가 아니라 "최종 결과"만 감사 로그에 남는다.
 */
@Aspect
@Component
@Order(2)
public class AuditAspect {

    private final AuditLog log;
    private final CurrentActor currentActor;

    public AuditAspect(AuditLog log, CurrentActor currentActor) {
        this.log = log;
        this.currentActor = currentActor;
    }

    @Around("@annotation(lab.sampleapp.orderplatform.aop.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Audited audited = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(Audited.class);
        String actorId = currentActor.get().id();
        try {
            Object result = joinPoint.proceed();
            log.record(actorId, audited.action(), true, "ok");
            return result;
        } catch (Throwable ex) {
            log.record(actorId, audited.action(), false, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
