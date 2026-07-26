package lab.ext.timing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class TimingInvocationHandler implements InvocationHandler {

    private final Object target;

    TimingInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!method.isAnnotationPresent(MeasureTime.class)) {
            return invokeTarget(method, args);
        }

        long start = System.nanoTime();
        try {
            return invokeTarget(method, args);
        } finally {
            TimingLog.record(method.getName(), System.nanoTime() - start);
        }
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // 프록시는 투명해야 한다 - 호출자에게 InvocationTargetException이 아니라
            // 대상 메서드가 실제로 던진 예외가 그대로 보여야 한다.
            throw e.getCause();
        }
    }
}
