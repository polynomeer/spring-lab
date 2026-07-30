# 표준 조건과 커스텀 조건이 같은 리포트로 수렴하는 지점

[`conditional-configuration.md`](../conditional-configuration.md)의 6번(호출 흐름) 항목을 시각화한 것. 서로 다른 애노테이션(`@ConditionalOnClass`/`@ConditionalOnProperty`/커스텀 `@ConditionalOnSlowMode`)이 전부 같은 `SpringBootCondition` 계약을 거쳐 같은 `ConditionEvaluationReport`로 모인다.

```mermaid
flowchart TD
    A["@ConditionalOnClass(name=...)"] --> D["Condition#matches()"]
    B["@ConditionalOnProperty(...)"] --> D
    C["@ConditionalOnSlowMode<br/>(커스텀)"] --> D

    D --> E["SpringBootCondition#matches()"]
    E --> F["getMatchOutcome(context, metadata)<br/>- 서브클래스마다 다른 구현"]

    F --> F1["OnClassCondition:<br/>ClassNameFilter.PRESENT/MISSING"]
    F --> F2["OnPropertyCondition:<br/>environment.getProperty(key)"]
    F --> F3["OnSlowModeCondition(커스텀):<br/>environment.getProperty(&quot;lab.slow-mode&quot;)"]

    F1 --> G["ConditionOutcome(matched, message)"]
    F2 --> G
    F3 --> G

    G --> H["ConditionEvaluationReport.recordConditionEvaluation()<br/>- 표준/커스텀 구분 없이 동일하게 기록"]

    style D fill:#333,color:#fff
    style H fill:#161,color:#fff
```
