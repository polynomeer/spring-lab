# "감싸는 프록시" vs "상속해서 대신 만든 객체" — self-invocation이 갈리는 지점

[`lookup-method-injection.md`](../lookup-method-injection.md)의 6번(호출 흐름)·11번(설계 의도) 항목을 시각화한 것. 왼쪽(25·26·29번)은 대상 객체와 프록시가 서로 다른 두 개체라서 `this` 호출이 프록시를 우회하고, 오른쪽(이번 주)은 Spring이 만드는 유일한 객체 자체가 이미 오버라이드된 서브클래스라서 `this` 호출도 그대로 오버라이드를 탄다.

```mermaid
flowchart TD
    subgraph AOP["25·26·29번: AOP 프록시 (감싸기)"]
        direction TB
        P["프록시 객체<br/>(별도 인스턴스)"] -->|"외부 호출은<br/>여기를 거침"| T["대상 객체 (target)<br/>(또 다른 별도 인스턴스)"]
        T -.->|"this.method()는<br/>target 안에서 target을<br/>직접 호출 - 프록시를<br/>거치지 않음"| T
    end

    subgraph LOOKUP["이번 주: @Lookup (상속해서 대신 만들기)"]
        direction TB
        C["CGLIB 서브클래스 인스턴스<br/>(Spring이 만드는 유일한 객체)"]
        C -->|"외부 호출도,<br/>this.nextTicket()도<br/>전부 이 객체 하나를 거침"| C
    end

    style T fill:#611,color:#fff
    style C fill:#161,color:#fff
```

```mermaid
sequenceDiagram
    participant Caller as 호출자
    participant Bean as LookupTicketSeller 빈<br/>(CGLIB 서브클래스, this 자신)
    participant BF as BeanFactory

    Caller->>Bean: sellViaSelfInvocation()
    Note over Bean: PASSTHROUGH 콜백 - 원본 메서드 본문 그대로 실행
    Bean->>Bean: this.nextTicket() 호출
    Note over Bean: this는 이미 LookupOverrideMethodInterceptor가<br/>연결된 바로 그 서브클래스 인스턴스
    Bean->>BF: getBeanProvider(Ticket.class).getObject()
    BF-->>Bean: 새 Ticket 인스턴스
    Bean-->>Caller: 매번 다른 Ticket
```
