package lab.sampleapp.orderplatform.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Retry(@Order(5))보다 바깥쪽에 둔다 - 이미 성공한 키라면 재시도 루프에 들어갈 필요 자체가
 * 없어야 하기 때문이다. IdempotencyStore는 supplier가 예외를 던지면 아무것도 캐시하지
 * 않으므로(ConcurrentHashMap#computeIfAbsent 계약), 실패한 시도는 같은 키로 다시 호출했을 때
 * 처음부터(재시도 포함) 다시 실행된다 - "성공한 결과만 멱등하게 재사용한다"는 의도적 선택.
 */
@Aspect
@Component
@Order(4)
public class IdempotencyAspect {

    private final IdempotencyStore store;

    public IdempotencyAspect(IdempotencyStore store) {
        this.store = store;
    }

    @Around("@annotation(lab.sampleapp.orderplatform.aop.IdempotencyGuarded)")
    public Object guard(ProceedingJoinPoint joinPoint) {
        String key = (String) joinPoint.getArgs()[0];
        return store.computeIfAbsent(key, () -> proceedUnchecked(joinPoint));
    }

    private Object proceedUnchecked(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error ex) {
            // 언체크 예외는 그대로 다시 던진다 - 바깥쪽 어드바이스(Auth/Audit/Timing)와
            // 테스트가 원래 예외 타입(예: TransientOperationException)을 그대로 보게 하려는
            // 의도다. Supplier<T>#get()이 checked Throwable을 던질 수 없어서 감싸는 건
            // 진짜 checked 예외(이 캡스톤의 대상 메서드들은 던지지 않는다)일 때뿐이다.
            throw ex;
        } catch (Throwable ex) {
            throw new IdempotentCallFailedException(ex);
        }
    }

    static class IdempotentCallFailedException extends RuntimeException {
        IdempotentCallFailedException(Throwable cause) {
            super(cause);
        }
    }
}
