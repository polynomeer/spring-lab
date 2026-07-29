package lab.experiments.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionPropagationTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        PropagationLog.reset();
        if (context != null) {
            context.close();
        }
    }

    private OrderService orderService() {
        context = TransactionPlaygroundLab.buildContext();
        context.getBean(LedgerRepository.class).clear();
        return context.getBean(OrderService.class);
    }

    @Test
    void requiredParticipatesInTheSameConnectionAsTheOuterTransaction() {
        OrderService orderService = orderService();

        orderService.placeOrderInnerRequired(false);

        ConnectionSnapshot order = PropagationLog.get("order");
        ConnectionSnapshot payment = PropagationLog.get("payment");
        assertThat(payment.connectionIdentity()).isEqualTo(order.connectionIdentity());
        assertThat(payment.transactionActive()).isTrue();
    }

    @Test
    void requiresNewSuspendsAndUsesADifferentConnection() {
        OrderService orderService = orderService();

        orderService.placeOrderInnerRequiresNew(false);

        ConnectionSnapshot order = PropagationLog.get("order");
        ConnectionSnapshot payment = PropagationLog.get("payment");
        assertThat(payment.connectionIdentity()).isNotEqualTo(order.connectionIdentity());
        assertThat(payment.transactionActive()).isTrue();
    }

    @Test
    void nestedSharesTheSameConnectionAsTheOuterTransaction() {
        OrderService orderService = orderService();

        orderService.placeOrderInnerNested(false);

        ConnectionSnapshot order = PropagationLog.get("order");
        ConnectionSnapshot payment = PropagationLog.get("payment");
        // NESTED는 REQUIRES_NEW와 달리 별도 커넥션/트랜잭션을 만들지 않는다 - 같은 커넥션 안의
        // savepoint로 구현된다.
        assertThat(payment.connectionIdentity()).isEqualTo(order.connectionIdentity());
    }

    @Test
    void notSupportedRunsWithNoActiveTransaction() {
        OrderService orderService = orderService();

        orderService.placeOrderInnerNotSupported(false);

        ConnectionSnapshot payment = PropagationLog.get("payment");
        assertThat(payment.transactionActive()).isFalse();
    }

    @Test
    void requiredInnerFailureUncaughtRollsBackEverything() {
        OrderService orderService = orderService();

        assertThatThrownBy(() -> orderService.placeOrderInnerRequired(true))
                .isInstanceOf(PaymentFailedException.class);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        assertThat(ledger.contains("order")).isFalse();
        assertThat(ledger.contains("payment")).isFalse();
    }

    @Test
    void requiredInnerFailureCaughtByOuterStillRollsBackEverythingAndThrowsUnexpectedRollback() {
        OrderService orderService = orderService();

        // outer는 PaymentFailedException을 내부에서 삼키고 정상적으로 리턴하려 하지만,
        // 이미 rollback-only로 표시된 트랜잭션을 commit 시도하면 프레임워크가 대신 롤백하고
        // UnexpectedRollbackException을 던진다 - outer 메서드 본문은 예외를 던진 적이 없는데도.
        assertThatThrownBy(() -> orderService.placeOrderCatchingInnerRequiredFailure(true))
                .isInstanceOf(UnexpectedRollbackException.class);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        assertThat(ledger.contains("order")).isFalse();
        assertThat(ledger.contains("payment")).isFalse();
        assertThat(ledger.contains("audit")).isFalse();
    }

    @Test
    void requiresNewInnerFailureCaughtByOuterDoesNotAffectTheOuterTransaction() {
        OrderService orderService = orderService();

        // REQUIRES_NEW는 독립된 트랜잭션이라 실패해도 그 자신만 롤백되고, outer의 트랜잭션은
        // rollback-only로 표시되지 않는다 - UnexpectedRollbackException이 나지 않는다.
        orderService.placeOrderCatchingInnerRequiresNewFailure(true);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        assertThat(ledger.contains("order")).isTrue();
        assertThat(ledger.contains("audit")).isTrue();
        assertThat(ledger.contains("payment")).isFalse();
    }

    @Test
    void nestedInnerFailureCaughtByOuterDoesNotAffectTheOuterTransaction() {
        OrderService orderService = orderService();

        // savepoint까지만 롤백되므로(11번 절 참고), NESTED 실패도 outer 트랜잭션 전체를
        // rollback-only로 만들지 않는다.
        orderService.placeOrderCatchingInnerNestedFailure(true);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        assertThat(ledger.contains("order")).isTrue();
        assertThat(ledger.contains("audit")).isTrue();
        assertThat(ledger.contains("payment")).isFalse();
    }

    @Test
    void requiresNewCommitsIndependentlyEvenWhenTheOuterTransactionLaterFails() {
        OrderService orderService = orderService();

        assertThatThrownBy(orderService::placeOrderRequiresNewSucceedsThenOuterFails)
                .isInstanceOf(IllegalStateException.class);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        // outer(order)는 롤백됐지만, REQUIRES_NEW로 이미 독립적으로 커밋된 payment는 남는다.
        assertThat(ledger.contains("order")).isFalse();
        assertThat(ledger.contains("payment")).isTrue();
    }

    @Test
    void notSupportedWriteIsNeverRolledBackBecauseItIsNeverTransactional() {
        OrderService orderService = orderService();

        // NOT_SUPPORTED로 실행된 코드는 트랜잭션이 전혀 없으므로 autocommit으로 즉시
        // 반영된다 - 그 뒤에 무슨 일이 있어도(여기서는 결제 자체가 실패해도) 되돌릴 수 없다.
        assertThatThrownBy(() -> orderService.placeOrderInnerNotSupported(true))
                .isInstanceOf(PaymentFailedException.class);

        LedgerRepository ledger = context.getBean(LedgerRepository.class);
        assertThat(ledger.contains("order")).isFalse();
        assertThat(ledger.contains("payment")).isTrue();
    }
}
