package lab.minispring.aop;

public interface MethodInterceptor {

    Object invoke(MethodInvocation invocation) throws Throwable;
}
