package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcAccountRepository implements Account {

    private final JdbcMiniTransactionManager transactionManager;

    public JdbcAccountRepository(JdbcMiniTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public void transfer(int accountId, int delta) {
        // DataSource에서 새 커넥션을 얻지 않는다 - 이 스레드에 이미 떠 있는(참여했든 주인이든)
        // 트랜잭션의 커넥션을 가져온다. 그래야 MiniTransactionInterceptor가 감싼 트랜잭션과
        // 같은 물리적 트랜잭션에서 실행된다.
        Connection connection = transactionManager.getCurrentConnection();
        int current = queryBalance(connection, accountId);
        if (current + delta < 0) {
            throw new IllegalStateException("insufficient balance");
        }
        updateBalance(connection, accountId, delta);
    }

    private int queryBalance(Connection connection, int accountId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("balance");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void updateBalance(Connection connection, int accountId, int delta) {
        try (PreparedStatement ps =
                connection.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
