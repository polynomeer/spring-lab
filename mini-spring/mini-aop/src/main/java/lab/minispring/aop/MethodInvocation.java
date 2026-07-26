package lab.minispring.aop;

import java.lang.reflect.Method;

public interface MethodInvocation {

    Method getMethod();

    Object[] getArguments();

    Object getTarget();

    Object proceed() throws Throwable;
}
