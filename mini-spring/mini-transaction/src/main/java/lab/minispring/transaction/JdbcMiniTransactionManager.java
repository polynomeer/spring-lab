package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

// 1단계(항상 새 트랜잭션)와 2단계(ThreadLocal 기반 기존 트랜잭션 참여)만 구현한다 - REQUIRED의
// rollback-only 전파, REQUIRES_NEW의 suspend/resume(3~4단계)은 14주차(전파 속성)로 미룬다.
public final class JdbcMiniTransactionManager implements MiniTransactionManager {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public JdbcMiniTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 실제 Spring의 DataSourceUtils.getConnection(dataSource)에 대응한다 - 대상 코드는
    // DataSource에서 직접 커넥션을 새로 얻는 게 아니라, 이 스레드에 이미 바인딩된 트랜잭션의
    // 커넥션을 가져와야 같은 물리적 트랜잭션 안에서 동작한다.
    public Connection getCurrentConnection() {
        Connection connection = connectionHolder.get();
        if (connection == null) {
            throw new IllegalStateException("no transaction active on this thread");
        }
        return connection;
    }

    @Override
    public MiniTransactionStatus begin() {
        Connection existing = connectionHolder.get();
        if (existing != null) {
            // 이미 이 스레드에 트랜잭션이 떠 있다 - 새로 만들지 않고 참여한다.
            return new MiniTransactionStatus(existing, false);
        }

        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            connectionHolder.set(connection);
            return new MiniTransactionStatus(connection, true);
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
        try {
            status.getConnection().commit();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to commit transaction", ex);
        } finally {
            release(status.getConnection());
        }
    }

    @Override
    public void rollback(MiniTransactionStatus status) {
        if (!status.isNewTransaction()) {
            // 2단계의 한계: 참여자가 실패해도 주인에게 "이 트랜잭션은 이제 롤백만 가능하다"는
            // rollback-only 표시를 전파하지 않는다 - 이 규칙은 REQUIRED를 제대로 구현하는
            // 14주차(3단계)에서 다룬다. 지금은 그냥 아무것도 하지 않고 예외만 위로 전파한다.
            return;
        }
        try {
            status.getConnection().rollback();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to roll back transaction", ex);
        } finally {
            release(status.getConnection());
        }
    }

    private void release(Connection connection) {
        connectionHolder.remove();
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to close connection", ex);
        }
    }
}
