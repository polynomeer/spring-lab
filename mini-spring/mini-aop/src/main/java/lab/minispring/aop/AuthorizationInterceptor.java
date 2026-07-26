package lab.minispring.aop;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class AuthorizationInterceptor implements MethodInterceptor {

    private final BooleanSupplier authorized;
    private final List<String> trace;

    public AuthorizationInterceptor(BooleanSupplier authorized, List<String> trace) {
        this.authorized = authorized;
        this.trace = trace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        trace.add("AuthorizationInterceptor before");
        if (!authorized.getAsBoolean()) {
            throw new SecurityException("Not authorized to call " + invocation.getMethod().getName());
        }
        try {
            return invocation.proceed();
        } finally {
            trace.add("AuthorizationInterceptor after");
        }
    }
}
