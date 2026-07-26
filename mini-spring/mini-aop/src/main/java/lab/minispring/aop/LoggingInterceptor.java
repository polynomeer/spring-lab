package lab.minispring.aop;

import java.util.List;

public final class LoggingInterceptor implements MethodInterceptor {

    private final List<String> trace;

    public LoggingInterceptor(List<String> trace) {
        this.trace = trace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        trace.add("LoggingInterceptor before");
        try {
            return invocation.proceed();
        } finally {
            trace.add("LoggingInterceptor after");
        }
    }
}
