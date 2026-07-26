package lab.experiments.proxy;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class CountingInterceptor implements MethodInterceptor {

    private final List<String> invokedMethodNames = new ArrayList<>();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        invokedMethodNames.add(invocation.getMethod().getName());
        return invocation.proceed();
    }

    public List<String> getInvokedMethodNames() {
        return invokedMethodNames;
    }

    public int getInvocationCount() {
        return invokedMethodNames.size();
    }
}
