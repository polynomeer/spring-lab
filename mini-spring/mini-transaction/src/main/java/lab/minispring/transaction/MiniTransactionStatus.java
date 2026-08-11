package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.Savepoint;

// isNewTransaction: 이 begin() 호출이 실제로 새 Connection/새 트랜잭션을 시작한
// "주인(owner)"인지, 아니면 스레드에 이미 떠 있는 트랜잭션에 "참여"만 한 것인지 구분한다.
// commit()/rollback()은 주인만 실제로 커밋/롤백하고 연결을 닫을 권한이 있다 - 참여자가
// 마음대로 커밋/롤백해 버리면 주인이 시작한 트랜잭션의 나머지 작업이 끊겨 버리기 때문이다.
public final class MiniTransactionStatus {

    private final MiniConnectionHolder holder;
    private final boolean newTransaction;
    private final MiniConnectionHolder suspendedHolder;
    private final Savepoint savepoint;

    MiniTransactionStatus(MiniConnectionHolder holder, boolean newTransaction, MiniConnectionHolder suspendedHolder) {
        this(holder, newTransaction, suspendedHolder, null);
    }

    MiniTransactionStatus(
            MiniConnectionHolder holder, boolean newTransaction, MiniConnectionHolder suspendedHolder,
            Savepoint savepoint) {
        this.holder = holder;
        this.newTransaction = newTransaction;
        this.suspendedHolder = suspendedHolder;
        this.savepoint = savepoint;
    }

    public Connection getConnection() {
        return holder.getConnection();
    }

    public boolean isNewTransaction() {
        return newTransaction;
    }

    MiniConnectionHolder getHolder() {
        return holder;
    }

    // REQUIRES_NEW로 시작하면서 기존 트랜잭션을 밀어냈다면(suspend) 그 기존 트랜잭션의
    // 홀더를 들고 있다가, 이 새 트랜잭션이 끝날 때 다시 스레드에 복원(resume)한다.
    MiniConnectionHolder getSuspendedHolder() {
        return suspendedHolder;
    }

    // NESTED로 기존 트랜잭션에 참여했다면(REQUIRES_NEW와 달리 같은 Connection 위에서) 그
    // savepoint를 들고 있다가, commit()에서는 해제(release)하고 rollback()에서는 이
    // savepoint까지만 되돌린다 - REQUIRED 참여자와 달리 holder를 rollback-only로 표시하지
    // 않는다(그래서 바깥 트랜잭션은 이 실패를 전혀 모른 채 계속 진행할 수 있다).
    boolean hasSavepoint() {
        return savepoint != null;
    }

    Savepoint getSavepoint() {
        return savepoint;
    }
}
