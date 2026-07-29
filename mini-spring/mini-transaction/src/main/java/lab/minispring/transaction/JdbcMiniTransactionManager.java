package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

// 발전 단계 1~4: 항상 새 트랜잭션 -> ThreadLocal 참여 -> REQUIRED -> REQUIRES_NEW.
// 5단계(rollback-only 전파)와 6단계(Synchronization callback)까지 포함한다.
// NESTED(savepoint)/SUPPORTS/NOT_SUPPORTED는 범위 밖으로 뒀다(11번 절 참고).
public final class JdbcMiniTransactionManager implements MiniTransactionManager {

    private final DataSource dataSource;
    private final ThreadLocal<MiniConnectionHolder> holderThreadLocal = new ThreadLocal<>();

    public JdbcMiniTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 실제 Spring의 DataSourceUtils.getConnection(dataSource)에 대응한다 - 대상 코드는
    // DataSource에서 직접 커넥션을 새로 얻는 게 아니라, 이 스레드에 이미 바인딩된 트랜잭션의
    // 커넥션을 가져와야 같은 물리적 트랜잭션 안에서 동작한다.
    public Connection getCurrentConnection() {
        MiniConnectionHolder holder = holderThreadLocal.get();
        if (holder == null) {
            throw new IllegalStateException("no transaction active on this thread");
        }
        return holder.getConnection();
    }

    public void registerSynchronization(MiniTransactionSynchronization synchronization) {
        MiniConnectionHolder holder = holderThreadLocal.get();
        if (holder == null) {
            throw new IllegalStateException("no transaction active on this thread");
        }
        holder.addSynchronization(synchronization);
    }

    @Override
    public MiniTransactionStatus begin(MiniPropagation propagation) {
        MiniConnectionHolder existing = holderThreadLocal.get();

        if (propagation == MiniPropagation.REQUIRED && existing != null) {
            // 이미 이 스레드에 트랜잭션이 떠 있다 - 새로 만들지 않고 참여한다.
            return new MiniTransactionStatus(existing, false, null);
        }

        // REQUIRED인데 기존 트랜잭션이 없거나, REQUIRES_NEW인 경우 - 새 트랜잭션을 시작한다.
        // REQUIRES_NEW라면 기존 트랜잭션(있다면)을 스레드에서 떼어내(suspend) 새 트랜잭션이
        // 끝난 뒤 되돌려 놓을(resume) 수 있도록 상태에 들고 있는다.
        MiniConnectionHolder suspended = (propagation == MiniPropagation.REQUIRES_NEW) ? existing : null;
        if (suspended != null) {
            holderThreadLocal.remove();
        }

        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            MiniConnectionHolder holder = new MiniConnectionHolder(connection);
            holderThreadLocal.set(holder);
            return new MiniTransactionStatus(holder, true, suspended);
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to begin transaction", ex);
        }
    }

    @Override
    public void commit(MiniTransactionStatus status) {
        if (!status.isNewTransaction()) {
            // 참여자는 커밋 권한이 없다 - 주인이 commit()할 때 함께 반영된다.
            return;
        }

        MiniConnectionHolder holder = status.getHolder();
        try {
            if (holder.isRollbackOnly()) {
                // 참여자 중 누군가가 실패해서 rollback()을 통해 여기에 표시를 남겼다(5단계) -
                // owner 입장에서는 정상적으로 리턴했지만, 실제로는 롤백하고 그 사실을 알려야 한다.
                doRollback(holder);
                throw new MiniUnexpectedRollbackException(
                        "transaction silently rolled back because it has been marked as rollback-only");
            }
            holder.notifyBeforeCommit();
            holder.getConnection().commit();
            holder.notifyAfterCommit();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to commit transaction", ex);
        } finally {
            release(status);
        }
    }

    @Override
    public void rollback(MiniTransactionStatus status) {
        if (!status.isNewTransaction()) {
            // 2단계까지는 여기서 아무것도 하지 않았다 - 참여자의 실패가 owner에게 전혀
            // 전파되지 않아서, owner가 나중에 commit()하면 참여자의 실패와 무관하게 그대로
            // 커밋돼 버리는 데이터 정합성 문제가 있었다. 5단계는 이를 고친다: 실제로 롤백하는
            // 대신, "이 트랜잭션은 이제 커밋하면 안 된다"는 표시만 남겨서 owner의 commit()이
            // 그 표시를 보고 대신 롤백하게 한다.
            status.getHolder().markRollbackOnly();
            return;
        }

        doRollback(status.getHolder());
        release(status);
    }

    private void doRollback(MiniConnectionHolder holder) {
        try {
            holder.getConnection().rollback();
            holder.notifyAfterRollback();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to roll back transaction", ex);
        }
    }

    private void release(MiniTransactionStatus status) {
        holderThreadLocal.remove();
        try {
            status.getHolder().getConnection().close();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to close connection", ex);
        } finally {
            if (status.getSuspendedHolder() != null) {
                // REQUIRES_NEW가 끝났다 - 밀어냈던 기존 트랜잭션을 스레드에 되돌려 놓는다.
                holderThreadLocal.set(status.getSuspendedHolder());
            }
        }
    }
}
