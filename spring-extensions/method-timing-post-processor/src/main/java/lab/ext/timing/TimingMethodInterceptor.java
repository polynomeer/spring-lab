package lab.ext.timing;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

final class TimingMethodInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            TimingLog.record(invocation.getMethod().getName(), System.nanoTime() - start);
        }
    }
}
