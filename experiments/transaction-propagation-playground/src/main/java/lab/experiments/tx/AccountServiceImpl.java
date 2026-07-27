package lab.experiments.tx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountServiceImpl implements AccountService {

    private final JdbcTemplate jdbcTemplate;

    public AccountServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void commitSuccessfully(int accountId, int delta) {
        adjustBalance(accountId, delta);
    }

    @Override
    @Transactional
    public void rollbackOnRuntimeException(int accountId, int delta) {
        adjustBalance(accountId, delta);
        throw new IllegalStateException("boom");
    }

    @Override
    @Transactional
    public void noRollbackOnCheckedExceptionByDefault(int accountId, int delta) throws Exception {
        adjustBalance(accountId, delta);
        throw new java.io.IOException("checked failure, but @Transactional's default rollback rule ignores it");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackOnCheckedExceptionWithRollbackFor(int accountId, int delta) throws Exception {
        adjustBalance(accountId, delta);
        throw new java.io.IOException("checked failure, but rollbackFor now covers it");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean readOnlyIsMarkedAsReadOnlyHint() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    @Override
    @Transactional(readOnly = true)
    public void readOnlyDoesNotPreventWritesByDefault(int accountId, int delta) {
        // DataSourceTransactionManager는 enforceReadOnly를 명시적으로 켜지 않는 한
        // readOnly=true를 실제 커넥션에 강제하지 않는다 - 그래서 이 쓰기는 예외 없이 성공한다.
        adjustBalance(accountId, delta);
    }

    @Override
    public void invokeRollbackMethodThroughSelfInvocation(int accountId, int delta) {
        // this.rollbackOnRuntimeException(...)은 프록시가 아니라 이 인스턴스로 직접 가므로,
        // @Transactional이 전혀 적용되지 않은 채로 실행된다.
        rollbackOnRuntimeException(accountId, delta);
    }

    @Override
    public boolean invokePrivateTransactionalMethod(int accountId, int delta) {
        return privateTransactionalUpdate(accountId, delta);
    }

    @Transactional
    private boolean privateTransactionalUpdate(int accountId, int delta) {
        adjustBalance(accountId, delta);
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    @Transactional
    public void exceptionCaughtInsideDoesNotTriggerRollback(int accountId, int delta) {
        adjustBalance(accountId, delta);
        try {
            throw new IllegalStateException("caught before it ever reaches TransactionInterceptor");
        } catch (IllegalStateException ignored) {
            // 여기서 삼켜지므로 TransactionInterceptor#invoke는 예외를 절대 보지 못한다.
        }
    }

    @Override
    public int balanceOf(int accountId) {
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?", Integer.class, accountId);
        return balance;
    }

    private void adjustBalance(int accountId, int delta) {
        jdbcTemplate.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", delta, accountId);
    }
}
