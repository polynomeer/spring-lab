package lab.minispring.transaction;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

// 실제 Spring의 ConnectionHolder에 대응한다 - 커넥션 자체뿐 아니라, 이 트랜잭션에 얽힌
// 부가 상태(rollback-only 여부, Synchronization 콜백 목록)까지 스레드에 함께 바인딩해야
// 해서 Connection을 직접 ThreadLocal에 넣는 대신 이 홀더로 감싼다.
final class MiniConnectionHolder {

    private final Connection connection;
    private boolean rollbackOnly;
    private final List<MiniTransactionSynchronization> synchronizations = new ArrayList<>();

    MiniConnectionHolder(Connection connection) {
        this.connection = connection;
    }

    Connection getConnection() {
        return connection;
    }

    void markRollbackOnly() {
        this.rollbackOnly = true;
    }

    boolean isRollbackOnly() {
        return rollbackOnly;
    }

    void addSynchronization(MiniTransactionSynchronization synchronization) {
        synchronizations.add(synchronization);
    }

    void notifyBeforeCommit() {
        synchronizations.forEach(MiniTransactionSynchronization::beforeCommit);
    }

    void notifyAfterCommit() {
        synchronizations.forEach(MiniTransactionSynchronization::afterCommit);
    }

    void notifyAfterRollback() {
        synchronizations.forEach(MiniTransactionSynchronization::afterRollback);
    }
}
