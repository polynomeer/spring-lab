# REQUIRED / REQUIRES_NEW / NESTED, 커넥션이 갈라지는 지점

[`transaction-propagation.md`](../transaction-propagation.md)의 6번(호출 흐름) 항목을 시각화한 것. 세 전파 속성 모두 "바깥 트랜잭션이 이미 하나 떠 있다"는 같은 출발점에서 시작하지만, 물리적 커넥션을 공유하는지, 실패가 바깥까지 번지는지가 완전히 갈린다.

```mermaid
flowchart TD
    A["outer: REQUIRED로 시작<br/>Connection C1 바인딩"] --> B{"inner의 전파 속성은?"}

    B -->|REQUIRED| C["기존 홀더(C1) 그대로 참여<br/>새 Connection 없음"]
    C --> C1{"inner 실패?"}
    C1 -->|예| C2["참여자 rollback() → C1에<br/>rollback-only 표시만(실제 rollback X)"]
    C2 --> C3["outer가 삼켜도 owner commit()이<br/>rollback-only를 보고 실제 rollback +<br/>UnexpectedRollbackException"]
    C1 -->|아니오| C4["outer가 나중에 C1을 커밋할 때<br/>inner의 변경도 함께 커밋"]

    B -->|REQUIRES_NEW| D["C1을 suspend(스레드에서 분리)<br/>새 Connection C2 획득 + 바인딩"]
    D --> D1{"inner 실패?"}
    D1 -->|예| D2["C2만 rollback + close<br/>C1 resume - outer는 전혀 모름"]
    D1 -->|아니오| D3["C2만 commit + close (즉시 확정)<br/>C1 resume - outer가 나중에 실패해도<br/>이 커밋은 되돌릴 수 없음"]

    B -->|NESTED| E["C1 위에 savepoint 생성<br/>새 Connection 없음"]
    E --> E1{"inner 실패?"}
    E1 -->|예| E2["savepoint까지만 rollback<br/>outer는 rollback-only로 표시되지 않음"]
    E1 -->|아니오| E3["savepoint 해제(release)<br/>outer가 나중에 C1을 커밋할 때 함께 반영"]

    style C3 fill:#611,color:#fff
    style D3 fill:#333,color:#fff
    style E2 fill:#161,color:#fff
```
