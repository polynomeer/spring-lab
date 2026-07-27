package lab.experiments.tx;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTransactionTest {

    @Test
    void commitSuccessfullyPersistsTheChange() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        accountService.commitSuccessfully(1, 10);

        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }

    @Test
    void runtimeExceptionRollsBackTheChange() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        assertThatThrownBy(() -> accountService.rollbackOnRuntimeException(1, 10))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accountService.balanceOf(1)).isEqualTo(100);
        context.close();
    }

    @Test
    void checkedExceptionDoesNotRollBackByDefault() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        assertThatThrownBy(() -> accountService.noRollbackOnCheckedExceptionByDefault(1, 10))
                .isInstanceOf(java.io.IOException.class);

        // 기본 규칙: unchecked(RuntimeException/Error)만 롤백한다 - checked exception은
        // 커밋된다. 자주 놀라는 지점이라 직접 확인한다.
        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }

    @Test
    void rollbackForExtendsTheDefaultRuleToCheckedExceptions() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        assertThatThrownBy(() -> accountService.rollbackOnCheckedExceptionWithRollbackFor(1, 10))
                .isInstanceOf(java.io.IOException.class);

        assertThat(accountService.balanceOf(1)).isEqualTo(100);
        context.close();
    }

    @Test
    void readOnlyTransactionIsMarkedAsReadOnlyHint() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        assertThat(accountService.readOnlyIsMarkedAsReadOnlyHint()).isTrue();
        context.close();
    }

    @Test
    void readOnlyDoesNotActuallyPreventWritesWithDefaultTransactionManagerSettings() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        accountService.readOnlyDoesNotPreventWritesByDefault(1, 10);

        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }

    @Test
    void selfInvocationNeverStartsATransactionAtAll() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        assertThatThrownBy(() -> accountService.invokeRollbackMethodThroughSelfInvocation(1, 10))
                .isInstanceOf(IllegalStateException.class);

        // this.rollbackOnRuntimeException(...)이 프록시를 거치지 않았으므로 트랜잭션 자체가
        // 시작되지 않았다 - JDBC autocommit으로 그대로 반영되어 롤백되지 않는다.
        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }

    @Test
    void privateTransactionalMethodNeverActuallyStartsATransaction() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        boolean transactionWasActive = accountService.invokePrivateTransactionalMethod(1, 10);

        assertThat(transactionWasActive).isFalse();
        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }

    @Test
    void exceptionCaughtInsideNeverReachesTheTransactionInterceptorSoItCommits() {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        AccountService accountService = context.getBean(AccountService.class);

        accountService.exceptionCaughtInsideDoesNotTriggerRollback(1, 10);

        assertThat(accountService.balanceOf(1)).isEqualTo(110);
        context.close();
    }
}
