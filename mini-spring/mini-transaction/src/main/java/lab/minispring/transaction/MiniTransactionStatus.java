package lab.minispring.transaction;

import java.sql.Connection;

// isNewTransaction: 이 begin() 호출이 실제로 새 Connection/새 트랜잭션을 시작한
// "주인(owner)"인지, 아니면 스레드에 이미 떠 있는 트랜잭션에 "참여"만 한 것인지 구분한다.
// commit()/rollback()은 주인만 실제로 커밋/롤백하고 연결을 닫을 권한이 있다 - 참여자가
// 마음대로 커밋/롤백해 버리면 주인이 시작한 트랜잭션의 나머지 작업이 끊겨 버리기 때문이다.
public final class MiniTransactionStatus {

    private final MiniConnectionHolder holder;
    private final boolean newTransaction;
    private final MiniConnectionHolder suspendedHolder;

    MiniTransactionStatus(MiniConnectionHolder holder, boolean newTransaction, MiniConnectionHolder suspendedHolder) {
        this.holder = holder;
        this.newTransaction = newTransaction;
        this.suspendedHolder = suspendedHolder;
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
}
