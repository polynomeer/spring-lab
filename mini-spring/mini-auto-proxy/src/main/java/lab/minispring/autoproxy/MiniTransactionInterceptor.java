package lab.minispring.autoproxy;

import java.util.List;

import lab.minispring.aop.MethodInterceptor;
import lab.minispring.aop.MethodInvocation;

public final class MiniTransactionInterceptor implements MethodInterceptor {

    private final List<String> transactionLog;

    public MiniTransactionInterceptor(List<String> transactionLog) {
        this.transactionLog = transactionLog;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        transactionLog.add("BEGIN " + invocation.getMethod().getName());
        try {
            Object result = invocation.proceed();
            transactionLog.add("COMMIT " + invocation.getMethod().getName());
            return result;
        } catch (Throwable ex) {
            transactionLog.add("ROLLBACK " + invocation.getMethod().getName());
            throw ex;
        }
    }
}
