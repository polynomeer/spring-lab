# 애플리케이션 이벤트 — 동기 멀티캐스트, 그리고 "커밋 후 실행"이 감추는 것

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 29(Application Event Bus)·프로젝트 30(Mini Event Multicaster)에 대응하는 분석 문서다. 로드맵의 필수 16주 밖에 있는 심화 주제라, 6~7단계(AOP·트랜잭션)에서 쓴 도구(프록시, `TransactionSynchronizationManager`)를 그대로 재사용한다.

## 1. 이번 질문

- `ApplicationEventPublisher.publishEvent()`를 호출하면 리스너는 정확히 언제, 어떤 스레드에서 실행되는가?
- 여러 리스너가 있을 때 순서는 어떻게 정해지고, 하나가 예외를 던지면 나머지는 실행되는가?
- `@TransactionalEventListener`는 "커밋 후 실행"을 어떻게 구현하는가 — 트랜잭션이 아예 없으면 무슨 일이 생기는가?
- 부모·자식 컨텍스트 구조에서 이벤트는 어느 범위까지 전파되는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Core, "Standard and Custom Events")은 `ApplicationEvent`를 상속하지 않은 평범한 객체(POJO)도 `publishEvent()`에 그대로 넘길 수 있다고 설명한다 - 내부적으로 `PayloadApplicationEvent`로 감싼다.
- `@EventListener`는 메서드 파라미터 타입으로 이벤트 타입을 추론하며, `condition` 속성으로 SpEL 조건부 실행을 지원한다고 명시한다.
- `@TransactionalEventListener`는 "현재 트랜잭션의 특정 단계(커밋 전/후, 롤백 후)에 리스너를 묶는다"고 설명하며, 트랜잭션이 없을 때는 `fallbackExecution`이 꺼져 있으면(기본값) 이벤트가 실행되지 않는다고 밝힌다.
- 기본 멀티캐스터(`SimpleApplicationEventMulticaster`)는 별도 `TaskExecutor`를 설정하지 않으면 완전히 동기적으로 동작한다고 설명한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Async` 없이 등록한 리스너도 발행 후 "곧" 실행될 거라고 예상했다 - **맞았다.** 동기 멀티캐스터는 리스너를 발행자 스레드에서 즉시, 순서대로 호출한다.
- 리스너 하나가 예외를 던지면 그 리스너만 실패하고 나머지는 계속 실행될 거라 예상했다 - **틀렸다.** 기본 `errorHandler`가 없으면 예외가 그대로 던져지고, `multicastEvent()`의 반복문 자체가 그 자리에서 멈춘다 - 이후 순서의 리스너는 아예 호출되지 않는다.
- `@TransactionalEventListener`가 트랜잭션이 없을 때는 그냥 즉시 동기 실행될 거라 예상했다(트랜잭션이 없으니 "즉시"가 "커밋 시점"과 같다고 생각했다) - **틀렸다.** `fallbackExecution` 기본값이 `false`라, 트랜잭션이 없으면 그 리스너는 조용히 버려진다 - 아예 실행되지 않는다.
- 자식 컨텍스트에서 발행한 이벤트는 자식 컨텍스트의 리스너에게만 전달될 거라 예상했다 - **틀렸다.** `AbstractApplicationContext#publishEvent`는 로컬 멀티캐스트 후 부모 컨텍스트에도 재귀적으로 같은 이벤트를 발행한다 - 부모의 리스너도 자식이 발행한 이벤트를 받는다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/application-event-lab`](../../experiments/application-event-lab)
```java
@Order(1)
@EventListener
public void explodesOnNegativeId(OrderCompletedEvent event) {
    if (event.orderId() < 0) throw new IllegalStateException("negative order id: " + event.orderId());
}

@TransactionalEventListener  // 기본 phase = AFTER_COMMIT
public void afterCommitListener(OrderCompletedEvent event) {
    eventLog.record("after-commit");
}
```

**축소 구현** — [`mini-spring/mini-event`](../../mini-spring/mini-event)
```java
public void publish(Object event) {
    registrations.stream()
            .filter(r -> r.eventType().isAssignableFrom(event.getClass()))
            .sorted(Comparator.comparingInt(Registration::order))
            .forEach(r -> dispatch(r, event));
}

private void dispatch(Registration<?> r, Object event) {
    if (r.afterCommit()) {
        if (!transactionManager.isTransactionActive()) return;   // fallbackExecution=false와 동일
        transactionManager.registerSynchronization(/* afterCommit()에서 실제 실행 */);
        return;
    }
    // 동기 또는 asyncExecutor.execute(...)
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ApplicationEventPublisher` | `publishEvent(Object)` - 실제 멀티캐스트는 위임, 발행자는 멀티캐스터 존재를 모른다 |
| `ApplicationEventMulticaster` / `SimpleApplicationEventMulticaster` | 등록된 리스너 목록 관리 + 실제 호출(동기 또는 `TaskExecutor` 위임) |
| `ApplicationListener<E>` | `E`가 반드시 `ApplicationEvent`를 상속해야 하는, 가장 기본적인 리스너 인터페이스 |
| `ApplicationListenerMethodAdapter` | `@EventListener` 메서드를 `ApplicationListener`로 감싸는 어댑터 - 파라미터 타입 추론, `condition` SpEL 평가, 리플렉션 호출을 전담 |
| `PayloadApplicationEvent<T>` | POJO 페이로드를 `ApplicationEvent`로 감싸는 래퍼 - `ApplicationListener<PayloadApplicationEvent<T>>`로 받거나, `@EventListener`가 자동으로 벗겨서 넘겨준다 |
| `TransactionalApplicationListenerMethodAdapter` | `@TransactionalEventListener`용 어댑터 - 실행을 즉시 하지 않고 `TransactionSynchronizationManager.registerSynchronization()`으로 미룬다 |
| (mini) `MiniEventMulticaster.Registration<E>` | 이벤트 타입 + 리스너 + order + async/afterCommit 플래그를 한데 묶은 등록 단위 |
| (mini) `JdbcMiniTransactionManager#registerSynchronization` | "커밋 후 실행"을 구현하기 위해 13~14주차 mini-transaction의 콜백 등록 API를 그대로 재사용 |

## 6. 호출 흐름

```text
[동기 @EventListener]
publishEvent(event)
  → multicastEvent(event, type)
  → getApplicationListeners(event, type) - 등록된 리스너 중 타입이 맞는 것만, order로 정렬
  → 리스너마다 invokeListener() - 발행자 스레드에서 순서대로, 즉시

[@Async @EventListener]
multicastEvent()의 for 루프 안에서, invokeListener()를 부르기 전에 분기한다
  → executor != null && listener.supportsAsyncExecution()
    - true → executor.execute(() -> invokeListener(...)) - 발행자는 기다리지 않고 다음 리스너로 진행
    - false → invokeListener()를 그 자리에서 직접(동기) 호출
  → 분기 자체가 invokeListener() 바깥이라, invokeListener()에 브레이크포인트를 걸면 동기든
    비동기든 항상 발행자 스레드에서 호출된 것으로 보인다 - 실제 스레드 전환은 그 프록시
    (AsyncExecutionInterceptor)를 통과하는 더 안쪽, listener.onApplicationEvent()가 실제
    대상 메서드를 리플렉션으로 호출하는 지점에서 일어난다. jdi-tracer로 직접 확인하고 나서
    고친 설명이다 - 처음엔 invokeListener() 자체가 스레드를 가를 거라 짐작했었다.

[@TransactionalEventListener(AFTER_COMMIT)]
publishEvent(event) → 즉시 실행되지 않는다
  → TransactionSynchronizationManager.isSynchronizationActive() 확인
    - false(트랜잭션 없음) && !fallbackExecution → 이벤트 버려짐
    - true → registerSynchronization(afterCommit 콜백)만 등록, 리턴
  → (나중에) 트랜잭션 커밋 → afterCommit 콜백들이 순서대로 실행

[부모·자식 컨텍스트]
child.publishEvent(event)
  → child의 멀티캐스터로 로컬 멀티캐스트
  → parent가 있으면 parent.publishEvent(event)를 재귀 호출 - parent의 멀티캐스터도 실행
```

## 7. 브레이크포인트

이번 주제는 실제 소스 확인(2·9번)과 런타임 관찰(8번)로 검증했다. 아래 5개는 이후 학습 대시보드의
event-multicast 시나리오([`experiments/application-event-lab`](../../experiments/application-event-lab)의
`ApplicationEventLab`)를 만들며 실제 jdi-tracer 세션으로 다시 검증했고, 그 과정에서 마지막 줄의
클래스/메서드 이름이 실제로는 달랐다는 걸 발견해 고쳤다 - `TransactionalApplicationListenerMethodAdapter`에는
`processEventWithCallback`이라는 메서드가 없다. `onApplicationEvent`는 `TransactionSynchronizationManager`가
활성 상태면 `TransactionalApplicationListenerSynchronization.register()`로 콜백 등록만 하고 리턴하고,
실제 지연 실행은 커밋/롤백 시점에 `AbstractPlatformTransactionManager`가 호출하는
`TransactionalApplicationListenerSynchronization$PlatformSynchronization#afterCompletion` →
`processEventWithCallbacks`(복수형)에서 일어난다 - 소스만 읽고 작성했을 때는 잡아내지 못했던
차이라, 실제 실행으로 검증하는 것의 가치를 다시 확인한 사례다.

```text
org.springframework.context.event.SimpleApplicationEventMulticaster#multicastEvent
org.springframework.context.event.SimpleApplicationEventMulticaster#invokeListener
org.springframework.context.support.AbstractApplicationContext#publishEvent
org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter#onApplicationEvent
org.springframework.transaction.event.TransactionalApplicationListenerSynchronization#processEventWithCallbacks
```

## 8. 런타임 관찰

[`ApplicationEventLabTest`](../../experiments/application-event-lab/src/test/java/lab/experiments/event/ApplicationEventLabTest.java) (7개):

| 실험 | 결과 |
| --- | --- |
| `@Order`로 지정한 여러 동기 리스너 발행 | 클래스와 무관하게 order 오름차순으로, 전부 발행자 스레드에서 실행 |
| `condition` SpEL이 거짓인 경우 | 해당 리스너만 조용히 건너뜀 - 다른 리스너에는 영향 없음 |
| `@Async` 리스너 | 발행자와 **다른** 스레드(`SimpleAsyncTaskExecutor-1`)에서 실행 |
| 리스너 하나가 예외를 던짐(errorHandler 없음) | 예외가 호출자에게 그대로 전파되고, 이후 순서의 리스너는 **전혀 호출되지 않음**(로그에 아무것도 안 남음) |
| `@TransactionalEventListener` + 커밋 | `after-commit` 로그가 남음, 일반 `@EventListener`는 발행 시점에 이미 실행 완료 |
| `@TransactionalEventListener` + 롤백 | `after-commit` 로그가 **절대** 남지 않음(일반 리스너는 여전히 실행됨) |
| 자식 컨텍스트에서 발행 | 부모 컨텍스트에 등록된 `ApplicationListener<PayloadApplicationEvent<...>>`도 이벤트를 받음 |

[`MiniEventMulticasterTest`](../../mini-spring/mini-event/src/test/java/lab/minispring/event/MiniEventMulticasterTest.java) (8개):

| 실험 | 결과 |
| --- | --- |
| order로 지정한 두 리스너 발행 | 오름차순으로 실행 |
| 상위 인터페이스(`OrderEvent`)로 등록한 리스너에 두 하위 타입 이벤트 발행 | 둘 다 수신(`isAssignableFrom` 기반이므로) |
| 기본 에러 정책(예외를 그대로 던짐) | 첫 리스너의 예외가 `publish()` 밖으로 전파, 이후 리스너는 호출 안 됨 |
| 커스텀 에러 정책(삼키고 계속) | 첫 리스너의 실패를 로그로 남기고, 다음 리스너는 정상 실행 |
| async 리스너 | 발행자와 다른 스레드에서 실행(`CountDownLatch`로 동기화해서 확인) |
| afterCommit 리스너 + 실제 commit | 커밋 후에만 실행 |
| afterCommit 리스너 + rollback | 실행 안 됨 |
| afterCommit 리스너인데 트랜잭션이 아예 없음 | 조용히 버려짐(실제 `fallbackExecution=false`와 동일) |

**직접 겪은 버그**: `EventLabConfig`가 처음에 `@ComponentScan(basePackageClasses = EventLabConfig.class)`로 패키지 전체를 스캔하도록 작성했는데, 테스트 코드의 정적 중첩 `@Configuration` 클래스(`ApplicationEventLabTest.ParentListenerConfig`, 부모·자식 컨텍스트 테스트용)가 메인 코드와 **같은 패키지**(`lab.experiments.event`)에 있었다. 테스트를 실행하면 컴파일된 테스트 클래스도 런타임 클래스패스에 함께 올라오므로, `@ComponentScan`이 이 헬퍼 설정 클래스까지 주워 담아 버려서 - 관련 없는 다른 테스트의 `eventLog`에 `parent-received-N` 로그가 섞여 들어왔다. `@ComponentScan`을 `@Import({OrderService.class, OrderEventListeners.class, FailureProneListener.class})`로 바꿔 대상을 명시하는 것으로 해결했다 - 패키지 스캔은 "테스트 소스셋도 같은 패키지면 함께 스캔된다"는 걸 실제로 겪어야 체감되는 위험이다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `ApplicationEventMulticasterTests`(`spring-context`), `TransactionalApplicationListenerMethodAdapterTests`/관련 통합 테스트(`spring-tx`)로 확인했다.

- **`SimpleApplicationEventMulticaster`의 `invokeListener`/`doInvokeListener` 소스**(직접 인용은 위 6·7번): `errorHandler`가 `null`이면 `doInvokeListener`를 그대로 호출하고 예외를 감싸지 않는다 - "예외가 그대로 전파되고 이후 리스너가 멈춘다"는 우리 관찰과 정확히 일치한다.
- **`TransactionalEventListener#fallbackExecution()` 기본값**: 애노테이션 소스에 `boolean fallbackExecution() default false;`로 명시돼 있고, Javadoc이 "트랜잭션이 없으면 `fallbackExecution`을 명시적으로 켜지 않는 한 이벤트가 버려진다"고 직접 밝힌다 - 우리가 "즉시 동기 실행될 것"이라 예상했던 것과 반대되는, 소스로 직접 반증한 지점이다.
- **`AbstractApplicationContext#publishEvent`의 부모 전파 로직**(421~467행): 로컬 멀티캐스트 후 `this.parent`가 있으면 재귀적으로 `publishEvent`를 호출한다 - 우리의 부모·자식 컨텍스트 테스트가 검증한 것과 정확히 같은 코드다.
- **mini의 "동기 실행 시 예외가 나면 이후 리스너가 멈춘다"는 정책이 실제로 공식 테스트로 검증되는지는 별도로 찾지 못했다** - `SimpleApplicationEventMulticasterTests`는 정상 케이스 위주였고, 예외 전파 자체는 소스 코드(`invokeListener`)로 직접 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-event`(project 30) — 카탈로그의 발전 과제 5가지를 모두 다룬다.

**구현한 것**
- `MiniEventMulticaster.publish(Object)`: 등록된 타입 중 `isAssignableFrom`으로 이벤트와 호환되는 것만 골라, `order` 오름차순으로 호출 (카탈로그 원본 스켈레톤의 `Map<Class<?>, List<EventListener<?>>>` 정확 매칭 대신, 상위 타입 매칭까지 지원하도록 발전시켰다)
- 동기/비동기: `addListener(..., boolean async, ...)` - 비동기면 생성자로 받은 `Executor`에 위임
- 리스너 예외 정책: `ErrorPolicy` - 기본값은 그대로 던지기(실제 Spring의 `errorHandler`가 없을 때와 동일), 교체 가능
- 커밋 후 실행: `afterCommit` 플래그 - `JdbcMiniTransactionManager.isTransactionActive()`/`registerSynchronization()`을 그대로 재사용해서, 트랜잭션이 없으면 버리고 있으면 커밋 콜백으로 미룬다

**생략한 것 (의도적)**
- **`condition` 같은 조건부 실행(SpEL)이 없다** - SpEL 파서 자체를 다루는 것은 이벤트 시스템과는 별개의 학습 주제라 범위 밖으로 뒀다. 필요하면 `addListener` 호출 전에 호출자가 직접 조건을 검사하면 된다.
- **`BEFORE_COMMIT`/`AFTER_ROLLBACK`/`AFTER_COMPLETION` phase가 없다** - `AFTER_COMMIT` 하나만 구현했다. mini-transaction의 `MiniTransactionSynchronization`은 이미 `beforeCommit`/`afterCommit`/`afterRollback` 콜백을 모두 갖고 있으므로, 나머지 phase도 같은 방식으로 어렵지 않게 추가할 수 있지만 카탈로그가 요구하는 최소 범위(트랜잭션 이벤트 하나)를 넘어서는 확장이라 미뤘다.
- **리스너 등록/해제 API가 없다** - 실제 `ApplicationContext`는 컨텍스트 종료 시 리스너를 함께 정리하지만, mini는 학습 목적상 등록만 지원한다.

## 11. Spring 설계 의도

- **왜 `ApplicationListener<E>`는 `E extends ApplicationEvent`를 강제하면서, `@EventListener`는 아무 POJO나 받는가**: `ApplicationListener`는 제네릭 인터페이스 하나로 "이 빈이 리스너다"라는 것을 타입 시스템 차원에서 표현해야 해서, 이벤트 타입을 컴파일 타임에 고정할 근거(`ApplicationEvent`)가 필요하다. 반면 `@EventListener`는 애노테이션 기반이라 런타임에 메서드 시그니처에서 파라미터 타입을 리플렉션으로 읽어내면 그만이므로, `ApplicationEvent` 상속이라는 제약 없이도 타입 추론이 가능하다 - 같은 목적(이벤트 구독)을 인터페이스 기반과 애노테이션 기반이라는 서로 다른 확장 지점으로 풀면서 제약 조건까지 달라진 사례다.
- **왜 기본 멀티캐스터는 동기이고, 비동기는 옵션인가**: 이벤트 발행은 "이 시점에 이 일이 일어났다"는 알림이지 원격 호출이 아니다. 기본값을 동기로 둔 덕에 "리스너가 실행 완료됐는지"를 발행자가 신경 쓸 필요 없이 `publishEvent()` 호출 하나로 끝나고, 실패도 그 자리에서 바로 드러난다. 비동기가 필요한 경우(느린 부가 작업)만 `@Async`로 명시적으로 옵트인하게 한 것은 "기본은 예측 가능하게, 예외는 명시적으로"라는 설계 원칙이다.
- **왜 `@TransactionalEventListener`는 트랜잭션이 없으면 기본적으로 이벤트를 버리는가(`fallbackExecution=false`)**: 이 애노테이션이 존재하는 이유 자체가 "커밋된 데이터만 보고 반응해야 하는 리스너"(예: 커밋된 주문에 대해서만 이메일을 보내야 하는 경우)를 위한 것이다. 트랜잭션이 없는 상태에서 "즉시 실행"해 버리면, 그 리스너가 (아직 커밋되지 않았거나 애초에 트랜잭션 없이 쓰인) 데이터를 보고 반응하게 되어 애초에 이 애노테이션을 쓴 의도 자체가 깨진다. 차라리 조용히 버려서 "이 리스너는 트랜잭션 컨텍스트 밖에서는 의미가 없다"는 것을 명확히 하고, 필요한 경우에만 `fallbackExecution=true`로 명시적으로 완화하게 한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@TransactionalEventListener`가 트랜잭션이 없을 때 "즉시 실행"이 아니라 "완전히 버려짐"이라는 것 - "이 리스너는 특정 트랜잭션 단계에 묶여 있다"는 게 "트랜잭션이 없으면 그 단계 자체가 존재하지 않으니 아예 실행 대상이 아니다"로 이어진다는 걸, `fallbackExecution` 기본값을 직접 확인하기 전까지는 놓치고 있었다.
- 예상 밖이었던 것: 리스너 예외가 "그 리스너만 실패"가 아니라 "멀티캐스트 루프 전체를 중단"시킨다는 것 - `@Order`로 세심하게 순서를 맞춰 놓아도, 앞쪽 리스너 하나의 버그가 뒤쪽 리스너 전체를 침묵시킬 수 있다는 뜻이다. 실무에서 여러 리스너를 독립적으로 동작시키고 싶다면 각 리스너 내부에서 예외를 직접 잡아야 한다는 실용적 교훈이다.
- Mini 구현이 보여준 것: 카탈로그의 원본 스켈레톤(`Map<Class<?>, List<EventListener<?>>>` 정확 매칭)을 그대로 구현했다면 상위 타입 리스너 테스트는 통과하지 못했을 것이다 - "이벤트 타입 매칭"이라는 한 줄짜리 요구사항 뒤에 실제로는 타입 계층 전체를 훑어야 하는 문제가 숨어 있었고, 이는 실제 `ApplicationListener`의 제네릭 타입 해석(`ResolvableType`)이 왜 그렇게 복잡하게 만들어졌는지를 거꾸로 이해하게 해 줬다.
- 6~7단계(AOP·트랜잭션)에서 만든 인터셉터/Synchronization 인프라가 이벤트 시스템이라는 전혀 다른 주제에서도 그대로 재사용됐다는 것 자체가, 왜 Spring이 "트랜잭션 동기화"를 트랜잭션 매니저 하나에 가두지 않고 별도 레지스트리(`TransactionSynchronizationManager`)로 분리했는지(14주차 문서 11번)를 다시 한번 확인시켜 준다.
