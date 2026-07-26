package lab.minispring.aop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class ReflectiveMethodInvocation implements MethodInvocation {

    private final Object target;
    private final Method method;
    private final Object[] arguments;
    private final List<MethodInterceptor> interceptors;

    private int index = -1;

    public ReflectiveMethodInvocation(Object target, Method method, Object[] arguments,
            List<MethodInterceptor> interceptors) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
        this.interceptors = interceptors;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public Object proceed() throws Throwable {
        if (++index == interceptors.size()) {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
        return interceptors.get(index).invoke(this);
    }
}
