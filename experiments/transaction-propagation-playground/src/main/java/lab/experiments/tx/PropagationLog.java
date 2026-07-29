package lab.experiments.tx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class PropagationLog {

    private static final List<ConnectionSnapshot> snapshots = new CopyOnWriteArrayList<>();

    private PropagationLog() {
    }

    public static void capture(String label, DataSource dataSource) {
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean rollbackOnly = transactionActive && TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();

        // DataSourceUtils.getConnection()/releaseConnection()은 짝을 맞춰야 하는 참조 카운트
        // 방식이다 - 스냅샷을 찍는 이 잠깐 사이에만 빌리고 바로 반납한다.
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            snapshots.add(new ConnectionSnapshot(
                    label,
                    Thread.currentThread().getName(),
                    System.identityHashCode(connection),
                    connection.getAutoCommit(),
                    transactionActive,
                    rollbackOnly));
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    public static List<ConnectionSnapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    public static ConnectionSnapshot get(String label) {
        return snapshots.stream()
                .filter(s -> s.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no snapshot recorded for " + label));
    }

    public static void reset() {
        snapshots.clear();
    }
}
