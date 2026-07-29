package lab.experiments.tx;

// 14주차 실험 하나를 실행할 때마다 "지금 이 지점에서 트랜잭션/커넥션 상태가 어떤가"를
// 기록하기 위한 스냅샷 - 카탈로그(프로젝트 21)가 요구하는 6개 항목 그대로다.
public record ConnectionSnapshot(
        String label,
        String threadName,
        int connectionIdentity,
        boolean autoCommit,
        boolean transactionActive,
        boolean rollbackOnly) {
}
