package lab.minispring.aop;

import java.util.HashMap;
import java.util.Map;

public final class CallCountInterceptor implements MethodInterceptor {

    private final Map<String, Integer> counts = new HashMap<>();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        counts.merge(invocation.getMethod().getName(), 1, Integer::sum);
        return invocation.proceed();
    }

    public int getCount(String methodName) {
        return counts.getOrDefault(methodName, 0);
    }
}
