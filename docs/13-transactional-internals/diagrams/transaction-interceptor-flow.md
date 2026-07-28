# TransactionInterceptor의 커밋/롤백 분기, 그리고 애초에 거기 도달하지 못하는 경로

[`transactional-internals.md`](../transactional-internals.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 정상적으로 프록시를 거친 호출이 커밋/롤백으로 갈리는 지점, 오른쪽은 self-invocation·private 메서드가 이 흐름에 아예 진입하지 못하는 이유다.

```mermaid
flowchart TD
    A["프록시를 거친 외부 호출"] --> B["TransactionInterceptor#invoke"]
    B --> C["tm.getTransaction(txAttr)<br/>TransactionStatus 확보"]
    C --> D["invocation.proceedWithInvocation()<br/>(대상 메서드 실제 실행)"]
    D -->|정상 반환| E["tm.commit(status)"]
    D -->|예외 발생| F["completeTransactionAfterThrowing"]
    F --> G{"txAttr.rollbackOn(ex)?"}
    G -->|"RuntimeException/Error,<br/>또는 rollbackFor 매칭"| H["tm.rollback(status)"]
    G -->|"checked 예외,<br/>기본 규칙"| I["tm.commit(status)<br/>(커밋된다!)"]
    H --> J["원래 예외를 그대로 재던짐"]
    I --> J

    style I fill:#611,color:#fff
    style G fill:#333,color:#fff
```

```mermaid
flowchart TD
    A2["같은 클래스 안에서 메서드 호출"] --> B2{"this.method() 인가,<br/>프록시.method() 인가?"}
    B2 -->|"this.method()<br/>(self-invocation)"| C2["원본 인스턴스로 직접 호출<br/>TransactionInterceptor를 아예 거치지 않음"]
    B2 -->|"프록시.method()"| D2{"대상 메서드가<br/>private인가?"}
    D2 -->|예| E2["프록시가 오버라이드할 수 없는 메서드<br/>애초에 프록시 대상 후보에서 빠짐"]
    D2 -->|아니오| F2["정상적으로 TransactionInterceptor 진입"]

    style C2 fill:#611,color:#fff
    style E2 fill:#611,color:#fff
    style F2 fill:#161,color:#fff
```
