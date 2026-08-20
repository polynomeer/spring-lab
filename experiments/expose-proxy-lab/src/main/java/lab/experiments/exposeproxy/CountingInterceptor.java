package lab.experiments.exposeproxy;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class CountingInterceptor implements MethodInterceptor {

    private final AtomicInteger greetCount = new AtomicInteger();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if ("greet".equals(invocation.getMethod().getName())) {
            greetCount.incrementAndGet();
        }
        return invocation.proceed();
    }

    public int greetCount() {
        return greetCount.get();
    }
}
