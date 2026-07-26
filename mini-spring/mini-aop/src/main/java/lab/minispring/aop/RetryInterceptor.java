package lab.minispring.aop;

import java.lang.reflect.InvocationTargetException;

// MethodInvocation은 proceed()가 인덱스를 한 방향으로만 전진시키는 1회용 객체라서(6주차
// ReflectiveMethodInvocation 참고), 재시도 시도마다 체인을 처음부터 다시 태울 방법이 없다.
// 그래서 이 인터셉터는 invocation.proceed() 대신 target 메서드를 직접 리플렉션으로 호출한다 -
// 그 결과 이 인터셉터보다 "안쪽"(target에 더 가까운 순서)에 다른 인터셉터를 두면 재시도마다
// 다시 실행되지 않는다. 체인의 가장 안쪽(target 바로 앞)에 배치해야 하는 제약이 있다.
public final class RetryInterceptor implements MethodInterceptor {

    private final int maxAttempts;
    private final Class<? extends Throwable> retryOn;

    public RetryInterceptor(int maxAttempts, Class<? extends Throwable> retryOn) {
        this.maxAttempts = maxAttempts;
        this.retryOn = retryOn;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return invocation.getMethod().invoke(invocation.getTarget(), invocation.getArguments());
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (!retryOn.isInstance(cause)) {
                    throw cause;
                }
                lastFailure = cause;
            }
        }
        throw lastFailure;
    }
}
