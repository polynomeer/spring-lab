package lab.sampleapp.orderplatform.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 가장 바깥쪽(@Order(1))에 둔다 - 재시도(RetryAspect)까지 포함한 "호출자 입장에서 걸린
 * 전체 시간"을 재고 싶기 때문이다. 재시도 안쪽에 뒀다면 성공한 마지막 시도의 시간만 재고
 * 실패한 앞선 시도들의 비용은 누락됐을 것이다.
 */
@Aspect
@Component
@Order(1)
public class TimingAspect {

    private final TimingLog log;

    public TimingAspect(TimingLog log) {
        this.log = log;
    }

    @Around("@annotation(lab.sampleapp.orderplatform.aop.Timed)")
    public Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            log.record(joinPoint.getSignature().toShortString(), System.nanoTime() - start);
        }
    }
}
