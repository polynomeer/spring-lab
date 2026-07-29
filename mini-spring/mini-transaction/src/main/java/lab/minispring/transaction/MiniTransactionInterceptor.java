package lab.minispring.transaction;

import lab.minispring.aop.MethodInterceptor;
import lab.minispring.aop.MethodInvocation;

// 실제 Spring의 TransactionInterceptor는 메서드마다 TransactionAttributeSource로 전파
// 속성을 조회한다 - mini는 애노테이션 기반 조회 대신, 인터셉터 인스턴스 하나당 전파 속성
// 하나를 고정으로 받는다(mini-auto-proxy의 Advisor 하나당 정책 하나 패턴과 동일하다).
public final class MiniTransactionInterceptor implements MethodInterceptor {

    private final MiniTransactionManager transactionManager;
    private final MiniPropagation propagation;

    public MiniTransactionInterceptor(MiniTransactionManager transactionManager, MiniPropagation propagation) {
        this.transactionManager = transactionManager;
        this.propagation = propagation;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        MiniTransactionStatus status = transactionManager.begin(propagation);
        // commit()은 일부러 이 try 밖에 둔다 - commit() 자체가
        // MiniUnexpectedRollbackException을 던질 수 있는데(5단계), 그걸 이 안의 catch가
        // "proceed()가 실패했다"는 신호로 오인해서 이미 끝난 트랜잭션을 또 rollback()하려고
        // 하면 안 되기 때문이다. 실제 Spring의 TransactionAspectSupport도 commit 호출을
        // proceed()를 감싸는 try/catch 밖에 둔다 - 처음엔 이 둘을 하나로 묶었다가 바로 이
        // 문제로 실패하는 걸 보고 구조를 고쳤다(14주차 문서 참고).
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable ex) {
            transactionManager.rollback(status);
            throw ex;
        }
        transactionManager.commit(status);
        return result;
    }
}
