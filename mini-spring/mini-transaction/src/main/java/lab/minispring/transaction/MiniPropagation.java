package lab.minispring.transaction;

// 실제 Spring의 Propagation 중 두 가지만 재현한다 - NESTED(savepoint)/SUPPORTS/
// NOT_SUPPORTED 등은 14주차의 mini 범위 밖으로 뒀다(카탈로그의 발전 단계는 REQUIRED,
// REQUIRES_NEW까지만 요구한다).
public enum MiniPropagation {
    REQUIRED,
    REQUIRES_NEW
}
