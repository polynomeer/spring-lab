package lab.minispring.transaction;

// 실제 Spring의 Propagation 중 세 가지를 재현한다 - SUPPORTS/NOT_SUPPORTED 등은 여전히
// 범위 밖이다(카탈로그의 발전 단계는 원래 REQUIRED, REQUIRES_NEW까지만 요구했다). NESTED는
// 회고(docs/retrospective/retrospective.md 7번 절)가 "남겨 둔 질문"으로 미뤄 뒀던 걸
// 나중에 채운 것이다 - docs/14-transaction-propagation 문서의 후기 참고.
public enum MiniPropagation {
    REQUIRED,
    REQUIRES_NEW,
    NESTED
}
