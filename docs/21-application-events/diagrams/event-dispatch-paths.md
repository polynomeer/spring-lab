# 이벤트 하나가 발행된 뒤 세 갈래로 갈라지는 지점

[`application-events.md`](../application-events.md)의 6번(호출 흐름) 항목을 시각화한 것. `publishEvent()` 호출 하나가 리스너의 등록 방식(동기/`@Async`/`@TransactionalEventListener`)에 따라 완전히 다른 실행 시점을 갖는다는 것, 그리고 부모 컨텍스트로의 재귀 전파가 이와 별개로 항상 일어난다는 것을 함께 보여준다.

```mermaid
flowchart TD
    A["orderService.completeOrder(event)"] --> B["child.publishEvent(event)"]

    B --> C["child의 SimpleApplicationEventMulticaster<br/>.multicastEvent(event)"]
    B --> P{"parent 컨텍스트가 있는가?"}
    P -->|예| P1["parent.publishEvent(event) 재귀 호출<br/>- parent의 리스너도 동일하게 아래 갈래를 탄다"]
    P -->|아니오| P2["전파 없음"]

    C --> D["getApplicationListeners(event, type)<br/>- 타입 매칭 + @Order 정렬"]
    D --> E{"리스너의 실행 방식은?"}

    E -->|"일반 @EventListener"| F["invokeListener() - 발행자 스레드에서<br/>즉시, 순서대로 실행"]
    F --> F1{"리스너가 예외를 던졌는가?"}
    F1 -->|예, errorHandler 없음| F2["예외가 그대로 전파<br/>루프 중단 - 이후 리스너 호출 안 됨"]
    F1 -->|아니오| F3["다음 리스너로 계속"]

    E -->|"@Async @EventListener"| G["executor.execute(() -> invokeListener())<br/>- 발행자는 기다리지 않고 바로 다음 리스너로"]
    G --> G1["별도 스레드에서 나중에 실행<br/>(순서 보장 없음, 발행자 예외와 무관)"]

    E -->|"@TransactionalEventListener(AFTER_COMMIT)"| H{"TransactionSynchronizationManager<br/>.isSynchronizationActive()?"}
    H -->|"false && !fallbackExecution(기본값)"| H1["이벤트 버려짐 - 실행 자체가 없음"]
    H -->|true| H2["registerSynchronization(afterCommit 콜백)만 등록<br/>- 지금은 실행 안 됨"]
    H2 --> H3["(나중에) 트랜잭션 커밋 시점에<br/>afterCommit 콜백이 순서대로 실행"]

    style F2 fill:#611,color:#fff
    style H1 fill:#611,color:#fff
    style H3 fill:#161,color:#fff
```
