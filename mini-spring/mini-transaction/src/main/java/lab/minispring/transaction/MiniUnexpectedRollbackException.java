package lab.minispring.transaction;

// 실제 Spring의 UnexpectedRollbackException에 대응한다 - "이 코드는 정상적으로 리턴했다고
// 생각하지만, 참여했던 트랜잭션이 이미 rollback-only로 표시돼 있어서 실제로는 롤백됐다"는
// 것을 owner에게 알리기 위한 신호다.
public class MiniUnexpectedRollbackException extends RuntimeException {

    public MiniUnexpectedRollbackException(String message) {
        super(message);
    }
}
