package lab.minispring.aop;

import java.util.ArrayList;
import java.util.List;

public final class TimingInterceptor implements MethodInterceptor {

    private final List<String> trace;
    private final List<Long> durationsNanos = new ArrayList<>();

    public TimingInterceptor(List<String> trace) {
        this.trace = trace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        trace.add("TimingInterceptor before");
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            durationsNanos.add(System.nanoTime() - start);
            trace.add("TimingInterceptor after");
        }
    }

    public List<Long> getDurationsNanos() {
        return durationsNanos;
    }
}
