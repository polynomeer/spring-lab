# 트랜잭셔널 아웃박스 — DB 트랜잭션이 보장하는 것과 보장하지 않는 것

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 23(Transactional Outbox Sample)에 대응하는 분석 문서다. 다른 문서들과 달리 Spring 내부 구조 하나를 파고드는 게 아니라, 7주차(트랜잭션)와 21주차(애플리케이션 이벤트)에서 배운 것들이 실제 백엔드 설계 문제(DB와 메시지 브로커에 걸친 쓰기)에서 왜 부족한지, 그리고 그 부족함을 어떻게 메우는지를 다룬다.

## 1. 이번 질문

- `@Transactional`이 커밋을 보장하는 범위는 정확히 어디까지인가 - 그 경계를 벗어난 부작용(메시지 발행)도 함께 보장되는가?
- `@TransactionalEventListener(AFTER_COMMIT)`으로 "커밋 후에 메시지를 보낸다"고 하면, 그 발행 자체가 실패했을 때 무슨 일이 생기는가?
- 아웃박스 패턴은 이 문제를 정확히 어떤 방식으로 피해 가는가?
- "적어도 한 번(at-least-once)" 전달을 전제하면, 컨슈머 쪽에서는 무엇을 반드시 구현해야 하는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Transaction Management, "Transaction-bound Events")은 `@TransactionalEventListener`가 "현재 트랜잭션이 성공적으로 커밋된 후"에 리스너를 실행할 수 있게 해 준다고 설명하지만, 그 리스너 실행 자체가 실패했을 때의 복구는 애플리케이션의 책임이라고 명시적으로 범위를 긋지는 않는다 - 이 문서가 실측으로 직접 채워야 하는 공백이다.
- 이 주제 자체(아웃박스 패턴)는 Spring 공식 문서의 대상이 아니다 - "메시지 발행과 로컬 트랜잭션을 원자적으로 묶는 표준 패턴"이라는 것은 분산 시스템/메시징 커뮤니티의 확립된 관용구(마이크로서비스 패턴)이며, 이 문서는 그 패턴을 Spring이 이미 제공하는 트랜잭션/이벤트 인프라 위에서 어떻게 조립하는지를 다룬다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@TransactionalEventListener(AFTER_COMMIT)`을 쓰면 "커밋된 데이터에 대해서만 안전하게 반응한다"는 것까지는 맞지만, 그 리스너 안에서 브로커 전송이 실패해도 Spring이 뭔가 재시도를 해 줄 거라 예상했다 - **틀렸다.** 커밋은 이미 끝난 뒤이므로 리스너의 실패는 그냥 예외로 끝난다 - 그 사실을 기록해 둘 곳도, 재시도할 방법도 이 접근 자체에는 없다.
- 아웃박스 테이블에 이벤트를 써 두면 그 자체로 "전달 보장"이 될 거라 예상했다 - **부분적으로만 맞다.** 아웃박스는 "이벤트가 최소한 한 번은 시도된다"는 것만 보장한다 - 발행자가 브로커 전송 후 `published` 표시를 남기기 전에 죽으면, 같은 이벤트가 다시 발행될 수 있다(at-least-once이지 exactly-once가 아니다). 멱등성 처리 없이는 이 재전송이 곧 중복 처리로 이어진다.
- 컨슈머의 멱등성 처리를 "같은 메시지 ID를 봤으면 무시한다"는 애플리케이션 메모리 상의 로직으로 충분할 거라 예상했다 - **틀렸다.** 컨슈머 프로세스 자체가 재시작될 수 있으므로, "이미 처리했다"는 사실도 DB처럼 durable한 곳에 기록해야 재시작 후에도 유효하다.

## 4. 최소 재현 코드

**Dual write(원자성 없음)** — [`sample-app/transactional-outbox-order`](../../sample-app/transactional-outbox-order)
```java
@Transactional
public void placeOrder(Order order) {
    orderRepository.save(order);                        // DB 트랜잭션 안
    eventPublisher.publishEvent(new OrderPlacedEvent(order.id()));
}

@TransactionalEventListener  // AFTER_COMMIT - 이미 위 트랜잭션 밖이다
public void onOrderPlaced(OrderPlacedEvent event) {
    messageBroker.send(event.orderId(), "order-placed:" + event.orderId());  // 여기서 실패하면 끝
}
```

**Outbox(원자성 있음)**
```java
@Transactional
public void placeOrder(Order order, long outboxEventId) {
    orderRepository.save(order);                                          // 같은 트랜잭션
    outboxRepository.save(new OutboxEvent(outboxEventId, order.id(), ..., false));  // 같은 트랜잭션
}

// 별도 폴러 - 트랜잭션 밖에서, 실패해도 다음 호출에서 재시도 가능
public void publishPending() {
    for (OutboxEvent event : outboxRepository.findUnpublished()) {
        try {
            messageBroker.send(event.id(), event.payload());
            outboxRepository.markPublished(event.id());
        } catch (RuntimeException ex) {
            // published=false로 남는다 - 유실이 아니라 재시도 대상이 된다
        }
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `OrderRepository` / `OutboxRepository` | 주문 테이블과 아웃박스 테이블 - **같은 `DataSource`, 같은 트랜잭션 매니저**를 공유하는 게 핵심이다 |
| `OutboxEvent` | `published` 플래그를 가진, 아직 발행되지 않은(또는 발행된) 이벤트의 durable한 표현 |
| `OutboxOrderService` | 주문 저장 + 아웃박스 기록을 하나의 `@Transactional` 경계에 묶는 쓰기 측 |
| `OutboxPublisher` | 트랜잭션 밖에서 동작하는 폴러 - 미발행 이벤트를 찾아 발행 시도, 실패하면 그대로 두고 다음 기회에 넘긴다 |
| `ProcessedMessageRepository` | 컨슈머 쪽 멱등성 - "이 메시지 ID를 이미 처리했는가"를 DB에 기록해 재시작에도 살아남게 한다 |
| `NaiveOrderService` / `NaiveOrderPlacedListener` | 대조군 - `@TransactionalEventListener(AFTER_COMMIT)`만으로 발행을 시도하는, 아웃박스가 없는 접근 |
| `MessageBroker` / `FakeMessageBroker` | 실제 브로커 대신 실패를 인위적으로 주입할 수 있는 인메모리 대역 |

## 6. 호출 흐름

```text
[Dual write - 원자성 없음]
NaiveOrderService.placeOrder()
  → @Transactional 시작
  → orderRepository.save() - DB에 기록
  → eventPublisher.publishEvent() - AFTER_COMMIT 콜백만 등록(21주차에서 확인한 그대로)
  → 메서드 리턴 → 트랜잭션 커밋 (여기서 이미 주문은 영구 저장됨)
  → afterCommit 콜백 실행 → messageBroker.send() 시도
      → 실패하면? 이미 커밋은 끝났고, 이 실패를 기록해 둘 DB 행이 애초에 없다
      → 결과: 주문은 존재하지만 그 사실을 아는 시스템은 DB뿐 - 메시지는 영원히 유실

[Outbox - 원자성 있음]
OutboxOrderService.placeOrder()
  → @Transactional 시작
  → orderRepository.save() - 같은 트랜잭션
  → outboxRepository.save(published=false) - 같은 트랜잭션
  → 커밋 (주문과 아웃박스 이벤트가 함께 있거나, 함께 없거나 - 절대 반쪽만 있을 수 없음)

OutboxPublisher.publishPending() (별도 시점, 별도 트랜잭션 없음)
  → findUnpublished() - published=false인 행 조회
  → messageBroker.send() 시도
      → 성공 → markPublished() - 이제 다음 폴링에서 제외됨
      → 실패 → 아무것도 안 함 - published=false 그대로, 다음 폴링이 자동으로 재시도

컨슈머 (at-least-once 배달 가정)
  → 메시지 수신 → processedMessageRepository.markProcessedIfNew(messageId)
      → 처음 보는 ID → true, 비즈니스 로직 실행
      → 이미 처리한 ID(재전송) → false, 아무것도 안 함(중복 처리 방지)
```

두 접근을 나란히 그린 시퀀스: [`diagrams/dual-write-vs-outbox.md`](diagrams/dual-write-vs-outbox.md)

## 7. 브레이크포인트

이번 주제는 별도 브레이크포인트 세션 없이, `TransactionalOutboxTest`의 세 시나리오를 직접 실행하고 DB 상태(`outbox_events.published`)와 `FakeMessageBroker`의 기록을 확인하는 것으로 검증했다 - 다루는 것이 Spring 내부 메서드가 아니라 애플리케이션 설계 자체이기 때문이다.

## 8. 런타임 관찰

[`TransactionalOutboxTest`](../../sample-app/transactional-outbox-order/src/test/java/lab/sampleapp/outbox/TransactionalOutboxTest.java) (3개):

| 실험 | 결과 |
| --- | --- |
| Dual write, 브로커가 `AFTER_COMMIT` 콜백 안에서 실패 | 주문은 DB에 정상적으로 남음(`existsById`=true), 그러나 브로커는 메시지를 **전혀** 받지 못함 - 재시도할 방법도 없음 |
| Outbox, 첫 발행 시도에서 브로커 실패 | `outbox_events.published`가 `false`로 그대로 남음, 브로커는 메시지를 받지 못함 |
| 같은 Outbox 이벤트, 두 번째 폴링(브로커 정상) | 이번엔 성공 - `published`가 `true`로 바뀌고, 브로커가 메시지를 받음 - **첫 실패가 유실로 이어지지 않았다** |
| 브로커가 같은 메시지를 두 번 배달(재전송 시뮬레이션) | `markProcessedIfNew()`가 첫 번째만 `true`를 반환 - 실제 처리는 정확히 한 번만 일어남 |

## 9. 공식 테스트 분석

이 주제는 Spring 프레임워크 자체의 기능이 아니라 그 위에 조립한 애플리케이션 설계 패턴이므로, 대응하는 `spring-framework` 공식 테스트가 존재하지 않는다. 대신:

- **`@TransactionalEventListener`가 실제로 "발행 실패를 복구해 주지 않는다"는 것**은 21주차 문서에서 이미 소스로 확인한 `TransactionalEventListener#fallbackExecution` 기본값(`false`)과 `AbstractPlatformTransactionManager#commit()`의 동작(13~14주차 문서)의 자연스러운 결론이다 - Spring은 "트랜잭션의 특정 시점에 콜백을 실행해 준다"는 것만 약속하고, 그 콜백 자체의 실패에 대한 복구는 명시적으로 범위 밖에 둔다.
- **아웃박스 패턴의 정합성**은 순전히 "같은 `DataSource`/트랜잭션 매니저를 공유하는 두 개의 INSERT가 하나의 트랜잭션 경계 안에 있다"는, 7주차에서 이미 검증한 로컬 트랜잭션의 원자성(`DataSourceTransactionManager`)에 의존한다 - 새로운 메커니즘이 아니라 기존 보장을 올바른 경계에 배치한 것뿐이다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`sample-app/transactional-outbox-order` — mini 재구현이 아니라 **실제 문제를 재현하는 통합 예제** 자체가 이 프로젝트의 산출물이다(카탈로그가 이 프로젝트를 `mini-spring/`이 아니라 `sample-app/`에 배치한 이유이기도 하다).

**구현한 것**
- Dual write(대조군)와 Outbox(해법) 두 접근을 나란히, 같은 스키마·같은 `FakeMessageBroker`로 비교
- 발행 실패 후 재시도(같은 `OutboxPublisher.publishPending()`을 두 번 호출)로 "실패해도 유실되지 않는다"를 직접 재현
- 컨슈머 쪽 멱등성(`ProcessedMessageRepository`, DB에 기록되는 processed-message 테이블)

**생략한 것 (의도적)**
- **실제 메시지 브로커(Kafka/RabbitMQ 등) 연동이 없다** - `FakeMessageBroker`는 인메모리이고 실패를 정확히 원하는 시점에 주입할 수 있어야 하는 이 실험의 목적에 맞춘 선택이다. 실제 브로커 클라이언트 라이브러리를 다루는 것은 이 저장소의 범위(Spring Framework/Boot 내부 구조)를 벗어난다.
- **스케줄러(`@Scheduled` 등)로 `OutboxPublisher`를 자동 실행하지 않는다** - 테스트가 `publishPending()`을 직접, 정확한 타이밍에 호출해서 "한 번의 폴링 주기"를 결정론적으로 재현한다. 실제 폴링 주기/백오프 전략은 이 실험의 핵심 질문(원자성)과는 별개의 운영 관심사라 범위 밖으로 뒀다.
- **아웃박스 이벤트의 정리(오래된 발행 완료 행 삭제)가 없다** - 실제로는 아웃박스 테이블이 무한정 커지지 않도록 주기적으로 정리해야 하지만, 이는 이 실험이 다루는 정합성 문제와는 다른 운영 관심사다.

## 11. Spring 설계 의도

- **왜 `@Transactional`은 DB 밖의 부작용(메시지 발행)까지 보장해 주지 않는가**: `PlatformTransactionManager`가 관리하는 것은 정확히 "하나의 트랜잭션 리소스"(대개 하나의 JDBC `Connection`)의 원자성이다(7주차 문서). 메시지 브로커는 그 리소스에 속하지 않는, 완전히 별개의 시스템이다 - Spring이 이 둘을 하나로 묶어 주지 않는 것은 태만이 아니라, 애초에 "분산 트랜잭션"(XA 등)이라는 훨씬 무겁고 다른 문제이기 때문이다. `@TransactionalEventListener`가 제공하는 것은 "커밋된 후에 실행을 미룬다"는 **타이밍 보장**이지, "그 실행 자체가 성공한다"는 **원자성 보장**이 아니다 - 이 둘을 구분하는 것이 이 프로젝트 전체의 핵심이다.
- **왜 아웃박스는 "별도 시스템과의 원자성 문제"를 "같은 시스템 안의 원자성 문제"로 바꾸는가**: 아웃박스 패턴의 통찰은 "두 시스템에 걸친 쓰기를 원자적으로 만드는 것"이 아니라, "발행해야 할 사실을 이미 원자성이 보장된 시스템(DB) 안에 durable하게 기록해 두고, 실제 발행은 그 기록을 근거로 나중에(그리고 몇 번이든) 재시도한다"는 것이다 - 문제를 어려운 쪽(분산 원자성)에서 쉬운 쪽(로컬 원자성 + 재시도 + 멱등성)으로 옮긴 것이다.
- **왜 멱등성은 아웃박스가 아니라 컨슈머의 책임인가**: 아웃박스가 보장하는 것은 "적어도 한 번은 시도된다"는 것뿐이고, "정확히 한 번만 전달된다"는 것은 분산 시스템에서 일반적으로 달성하기 어렵다(브로커 전송 성공 후 `markPublished()` 사이에 프로세스가 죽으면 재전송된다). 그래서 실무에서는 "발행 측은 최소 한 번을 보장하고, 수신 측은 중복을 걸러낸다"는 역할 분담이 표준이다 - 이 프로젝트의 `ProcessedMessageRepository`가 그 분담의 수신 측 절반이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@TransactionalEventListener(AFTER_COMMIT)`가 "안전한 이벤트 처리 방법"처럼 보이지만, 그 안전함은 "커밋된 데이터만 본다"는 것에 국한되고 "그 처리 자체가 안전하게 완료된다"는 것과는 무관하다는 것 - 21주차에서 배운 것("트랜잭션이 없으면 조용히 버려진다")과 이번 주제("트랜잭션이 있어도, 커밋 후 실행이 실패하면 마찬가지로 조용히 사라진다")가 사실 같은 근본 원인(그 콜백의 실행 자체는 트랜잭션의 보호 범위 밖이다)에서 나온다는 것을 이번에 연결해서 이해했다.
- 예상 밖이었던 것: 아웃박스 패턴을 "발행 실패를 막아 주는 장치"로 오해하기 쉬운데, 실제로는 "실패해도 재시도할 수 있는 상태를 보장해 주는 장치"에 가깝다 - 실패 자체는 여전히 일어날 수 있고(브로커가 계속 죽어 있다면), 아웃박스는 그 실패가 "잊혀지지 않는다"는 것만 보장한다.
- 이 프로젝트가 `docs/plan/00-methodology.md`의 순환(공식 문서 → 최소 예제 → ... → 축소 구현)과 다르게 진행된 이유 자체가 하나의 배움이다 - 모든 주제가 "Spring이 내부적으로 어떻게 구현했는가"를 묻는 것은 아니고, 이번처럼 "Spring이 제공하는 보장의 경계가 정확히 어디까지인가"를 실제 설계 문제 위에서 확인하는 것도 이 저장소가 다루려는 범위 안에 있다.
