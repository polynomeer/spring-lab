package lab.minispring.transaction;

import lab.minispring.aop.MethodInterceptor;
import lab.minispring.aop.MethodInvocation;

public final class MiniTransactionInterceptor implements MethodInterceptor {

    private final MiniTransactionManager transactionManager;

    public MiniTransactionInterceptor(MiniTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        MiniTransactionStatus status = transactionManager.begin();
        try {
            Object result = invocation.proceed();
            transactionManager.commit(status);
            return result;
        } catch (Throwable ex) {
            transactionManager.rollback(status);
            throw ex;
        }
    }
}
