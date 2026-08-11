# Mini Order Platform — 학습한 것을 하나의 앱으로 통합하기

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 16번 절 "종합 프로젝트 추천"에 대응하는 문서다. 지금까지의 32개 카탈로그 프로젝트가 각각 Spring 내부 구조 하나씩을 분리해서 파고들었다면, 이 프로젝트는 그렇게 배운 것들 — IoC, 빈 생명주기, AOP, 트랜잭션, MVC, 이벤트, Boot — 을 회원/상품/주문/결제/알림/감사 로그라는 하나의 도메인 위에서 서로 맞물리게 만드는 캡스톤이다.

카탈로그 자체가 이 프로젝트에는 코드 스켈레톤을 주지 않고 "적용할 학습 요소" 목록만 준다. 스켈레톤이 없다는 것 자체가 지금까지의 32개 프로젝트와 다른 지점이라, 이 문서는 처음부터 완성된 형태가 아니라 **단계(Phase)별로 진행 상황을 누적 기록**하는 방식으로 쓴다 — Phase가 끝날 때마다 이 문서의 해당 절을 채운다.

## 1. 도메인

```text
회원(Member)     - id, name, membership tier(BASIC/MEMBERSHIP)
상품(Product)    - id, name, priceWon, stock. Phase 1~6 완료 이후, 11번 절에서 추가
주문(Order)      - id, memberId, amountWon, status(PENDING/PAID/CANCELLED) - Phase 3
결제(Payment)    - PaymentGateway 전략(CARD/POINT), PaymentGatewayClient(외부 PG 흉내), payment_history(주문과 분리 저장) - Phase 1/3
알림(Notification) - NotificationChannel(email/sms), 컬렉션 주입으로 브로드캐스트
감사 로그        - AuditLog(인메모리, Phase 2) - Phase 5는 건드리지 않았다
```

도메인을 처음부터 다 만들지 않고 "이번 Phase가 요구하는 만큼만" 만든다 — 이 저장소 전체가 지켜온 "축소 구현" 원칙을 캡스톤 안에서도 그대로 따른 것이다. Phase 1~6(카탈로그가 요구한 21개 세부 기법) 동안은 Product 없이 `amountWon`을 그냥 파라미터로 받는 것으로 충분했다 - 애초에 "MVC를 다루려면 상품 목록 API가 있어야 한다"는 가정 자체가 틀렸었다. Phase 6까지 다 끝난 뒤에야, 카탈로그 범위 밖의 확장으로 Product를 추가했다(11번 절) - 그제서야 "주문 금액을 클라이언트가 부르게 해도 되는가"라는, 그전까지는 없었던 질문이 생겼다.

## 2. 모듈 구조

```text
sample-app/mini-order-platform/
  src/main/java/lab/sampleapp/orderplatform/
    domain/     - Member, MembershipTier
    payment/    - PaymentGateway 전략(Card/Point), PaymentGatewayClient, PaymentGatewayRegistry
    discount/   - DiscountPolicy 전략(@Qualifier로 명시 선택), PricingService
    notification/ - NotificationChannel(email/sms), NotificationDispatcher, SentNotificationLog
    plugin/     - SelfDescribingPlugin 마커, PluginCatalog, PluginRegistrationBeanPostProcessor
    aop/        - @Timed/@RequiresRole/@Audited/@Retryable/@IdempotencyGuarded 애노테이션 + 5개 @Aspect
    order/      - Order/OrderStatus/OrderRepository, PaymentHistory(REQUIRES_NEW), OrderOutbox,
                  OrderPlacementService, OrderValidator/OrderCancellationService(rollback-only), JdbcConfig
    web/        - @CurrentMember Resolver, PaymentMethodConverter, OrderResponseBodyAdvice,
                  OrderExceptionHandlers, OrderController/PaymentController, OrderWebConfig(@EnableWebMvc)
    event/      - OrderCompletedEvent, NotificationDispatchListener/OutboxPublishListener(AFTER_COMMIT),
                  OrderOutboxPublisher, OrderEventBroker/FakeOrderEventBroker
    boot/       - PaymentGatewayAutoConfiguration/NotificationAutoConfiguration(+Properties),
                  OrderPlatformApplication(@EnableAutoConfiguration 진입점)
    product/    - Product/ProductRepository(원자적 재고 차감), ProductService(관리자 전용 등록)
    OrderPlatformConfig.java - Phase 1~3, 5, 카탈로그 이후 확장용 진입점(web + OrderPlatformApplication 제외)
```

## 3. 학습 요소 → 구현 매핑

| 영역 | 카탈로그가 요구한 기법 | 상태 | 구현 위치 |
| --- | --- | --- | --- |
| IoC | 전략 구현체 등록 | ✅ Phase 1 | `PaymentGatewayRegistry` |
| IoC | 타입별 플러그인 구성 | ✅ Phase 1 | `NotificationChannel` 구현체들 |
| IoC | `@Qualifier` | ✅ Phase 1 | `PricingService` |
| IoC | 컬렉션 주입 | ✅ Phase 1 | `NotificationDispatcher` |
| 빈 생명주기 | 외부 클라이언트 초기화 | ✅ Phase 1 | `PaymentGatewayClient#connect` |
| 빈 생명주기 | 리소스 종료 | ✅ Phase 1 | `PaymentGatewayClient#disconnect` |
| 빈 생명주기 | 커스텀 BeanPostProcessor | ✅ Phase 1 | `PluginRegistrationBeanPostProcessor` |
| AOP | 실행 시간 측정 | ✅ Phase 2 | `TimingAspect` |
| AOP | 권한 검사 | ✅ Phase 2 | `AuthorizationAspect` |
| AOP | 감사 로그 | ✅ Phase 2 | `AuditAspect` |
| AOP | 재시도 | ✅ Phase 2 | `RetryAspect` |
| AOP | 멱등성 검사 | ✅ Phase 2 | `IdempotencyAspect` |
| 트랜잭션 | 주문+Outbox 저장 | ✅ Phase 3 | `OrderPlacementService` |
| 트랜잭션 | 결제 이력 분리(`REQUIRES_NEW`) | ✅ Phase 3 | `PaymentHistoryRecorder` |
| 트랜잭션 | rollback-only 실험 | ✅ Phase 3 | `OrderCancellationService` |
| MVC | 커스텀 인증 사용자 Resolver | ✅ Phase 4 | `CurrentMemberArgumentResolver` |
| MVC | 공통 응답 처리 | ✅ Phase 4 | `OrderResponseBodyAdvice`(+ project 26의 `ApiResponse<T>` 재사용) |
| MVC | 예외 처리 | ✅ Phase 4 | `OrderExceptionHandlers` |
| MVC | 커스텀 Converter | ✅ Phase 4 | `PaymentMethodConverter` |
| 이벤트 | 주문 완료 이벤트 | ✅ Phase 5 | `OrderCompletedEvent` |
| 이벤트 | 알림 발송 | ✅ Phase 5 | `NotificationDispatchListener` |
| 이벤트 | Outbox 발행 | ✅ Phase 5 | `OrderOutboxPublisher`/`OutboxPublishListener` |
| 이벤트 | 트랜잭션 커밋 이후 처리 | ✅ Phase 5 | `@TransactionalEventListener(phase = AFTER_COMMIT)` |
| Boot | 결제 클라이언트 AutoConfiguration | ✅ Phase 6 | `PaymentGatewayAutoConfiguration` |
| Boot | 알림 플러그인 Starter | ✅ Phase 6 | `NotificationAutoConfiguration` |
| Boot | 요청 관측 Starter | ✅ Phase 6 | `spring-extensions/mini-observability-starter` 재사용 |

## 4. Phase 1 — IoC + 빈 생명주기

### 4.1 세 가지 "복수 빈" 접근을 나란히 배치

같은 "여러 구현체 중 무엇을 쓸 것인가" 문제를 세 가지 다른 방식으로 풀어서 한 모듈 안에 나란히 뒀다 — 각각 언제 쓰는 게 맞는지 비교하려는 의도다.

| 방식 | 위치 | 언제 쓰는가 |
| --- | --- | --- |
| `List<T>` → 우리가 정의한 키로 `Map` 재구성 | `PaymentGatewayRegistry` (`PaymentMethod` 키), 이미 [`sample-app/plugin-discovery-system`](../07-component-scan/component-scan.md) §13에서 검증한 패턴 재사용 | 런타임에 "어떤 키가 들어올지 모르는" 동적 조회(결제 수단은 요청마다 다름) |
| `@Qualifier`로 생성자 파라미터에 명시 고정 | `PricingService`가 `DiscountPolicy` 두 후보 중 `"membership"`만 선택 | 컴파일 타임에 "이 서비스는 항상 이 구현체를 쓴다"고 결정할 수 있는 경우 |
| `List<T>` 그대로 순회(브로드캐스트) | `NotificationDispatcher`가 등록된 모든 채널에 전부 발송 | "하나를 고르는" 게 아니라 "전부에게 알린다"는 의미 자체가 다른 경우 |

세 번째(브로드캐스트)가 첫 번째(키로 조회)와 코드 모양은 비슷해 보이지만 의미가 다르다는 걸 실제로 나란히 짜 보니 확실히 체감됐다 — `List<T>` 주입 자체는 항상 같은 메커니즘(빈 이름 기준이 아니라 타입 기준 다건 매칭 + `@Order` 정렬)이고, 그걸 이후에 Map으로 바꾸느냐 그대로 순회하느냐는 순전히 소비하는 쪽의 설계 선택이라는 것.

### 4.2 빈 생명주기 — 컨테이너가 실제로 하는 일

`PaymentGatewayClient`를 컨테이너를 거치지 않고 `new`로 직접 만들면 `@PostConstruct`가 전혀 호출되지 않아서 `authorize()`가 즉시 `IllegalStateException`을 던진다(`chargingBeforeTheClientHasConnectedThrows` 테스트). 반대로 `AnnotationConfigApplicationContext`를 통해 얻으면 `refresh()` 시점에 이미 연결이 끝나 있고, `close()` 시점에 `@PreDestroy`가 호출돼 연결이 해제된다(`paymentGatewayClientConnectsOnRefreshAndDisconnectsOnClose` 테스트) — "DI 컨테이너가 하는 일은 단순 객체 생성이 아니라 생명주기 콜백까지 포함한다"는 걸 코드로 직접 재현한 것.

### 4.3 커스텀 BeanPostProcessor — 등록 방식과 무관하게 동작함을 검증

`PluginRegistrationBeanPostProcessor`는 `postProcessAfterInitialization`에서 `SelfDescribingPlugin`을 구현한 빈이면 무조건 `PluginCatalog`에 등록한다. 이 모듈의 플러그인 빈(`CardPaymentGateway`, `PointPaymentGateway`, `EmailNotificationChannel`, `SmsNotificationChannel`)은 전부 컴포넌트 스캔으로 등록되지만, BPP 코드 자체는 "어떻게 등록됐는지"를 전혀 알지 못하고 오직 `postProcessAfterInitialization(bean, beanName)`에 들어오는 매 빈에 대해서만 판단한다 — `@Bean` 메서드로 등록했어도 똑같이 동작했을 것이다.

**직접 확인한 함정**: `PluginRegistrationBeanPostProcessor`의 생성자가 `PluginCatalog`에 의존하는데, `BeanPostProcessor` 타입 빈은 Spring 컨테이너가 일반 빈보다 먼저(`registerBeanPostProcessors` 단계에서) 조기 인스턴스화한다 — 그 말은 `PluginCatalog`도 덩달아 조기 생성된다는 뜻이다. Spring 레퍼런스 문서는 이 조기 생성이 AOP 프록시 적용 같은 후속 처리를 우회시킬 수 있다고 명시적으로 경고한다. 여기서는 `PluginCatalog`가 다른 의존성이 없는 leaf 빈이라 문제가 없지만, 만약 `PluginCatalog`에 `@Transactional`이나 AOP 어드바이스가 걸려 있었다면 그 프록시가 적용되기 전 원본 객체가 BPP에 주입됐을 것이다 — Phase 2(AOP)에서 실제로 이 경계를 건드릴 계획이다.

### 4.4 테스트

[`MiniOrderPlatformIoCLifecycleTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/MiniOrderPlatformIoCLifecycleTest.java) (8개, 전부 실제 `AnnotationConfigApplicationContext` 기반):

| 테스트 | 확인하는 것 |
| --- | --- |
| `paymentGatewayRegistryResolvesByMethodViaListToMapReconstruction` | 정상 흐름 — CARD/POINT 각각 올바른 게이트웨이로 라우팅 |
| `duplicatePaymentMethodThrowsAtRegistryConstructionTime` | 경계 조건 — 같은 `PaymentMethod`를 구현하는 게이트웨이가 두 개면 생성자에서 즉시 실패 |
| `chargingAnUnregisteredMethodThrows` | 경계 조건 — 구현체가 없는 `PaymentMethod`(`BANK_TRANSFER`)로 조회하면 실패 |
| `pricingServicePicksTheMembershipQualifiedPolicyExplicitlyNotByDefault` | `@Qualifier`가 후보가 여럿이어도 모호성 예외 없이 정확히 지정한 빈을 고름 |
| `notificationDispatcherBroadcastsToEveryInjectedChannelInOrder` | 컬렉션 주입이 `@Order`대로 정렬되고, 전체 브로드캐스트가 실제로 전부에게 도달함 |
| `paymentGatewayClientConnectsOnRefreshAndDisconnectsOnClose` | 생명주기 — 컨테이너가 초기화/종료 콜백을 실제로 호출함 |
| `chargingBeforeTheClientHasConnectedThrows` | 경계 조건 — 컨테이너를 거치지 않으면 콜백이 호출되지 않음 |
| `customBeanPostProcessorAutoRegistersEveryPluginRegardlessOfHowItWasDeclared` | BPP가 등록 방식과 무관하게 모든 플러그인 빈을 정확히 수집함 |

## 5. Phase 2 — AOP

카탈로그가 요구한 5가지 관심사(실행 시간 측정/권한 검사/감사 로그/재시도/멱등성 검사)를 각각 독립된 `@Aspect`로 구현하고, `@EnableAspectJAutoProxy` 기반 실제 Spring AOP 프록시(11~12주차에서 이미 검증한 것과 같은 CGLIB 서브클래스 프록시 메커니즘)로 적용했다 - `mini-spring/mini-aop`처럼 축소 재구현하는 대신 실제 Spring AOP를 그대로 썼다(이 캡스톤 프로젝트 자체가 "배운 걸 실전에서 조립하는" 성격이라, mini 재구현은 이미 12주차에서 끝냈다고 보고 여기서는 반복하지 않기로 했다).

### 5.1 다섯 개 관심사가 겹쳤을 때의 순서

같은 메서드에 여러 애노테이션이 동시에 붙을 수 있으므로(`PaymentProcessingService` 참고), `@Order`로 명시적인 어드바이스 순서를 정했다 - 순서를 정하지 않으면 Spring이 등록 순서 등 예측하기 어려운 기준으로 어드바이스 체인을 구성하기 때문이다.

```text
바깥쪽 → 안쪽
@Order(1) TimingAspect        - 재시도까지 포함한 전체 소요 시간을 재야 하므로 가장 바깥
@Order(2) AuditAspect         - 재시도의 각 시도가 아니라 "최종 결과" 하나만 감사 로그에 남겨야 함
@Order(3) AuthorizationAspect - 권한이 없으면 캐시 조회/재시도 비용을 전혀 들이지 않고 즉시 실패
@Order(4) IdempotencyAspect   - 이미 성공한 키라면 재시도 루프 자체에 들어갈 필요가 없음
@Order(5) RetryAspect         - 실제 대상 메서드에 가장 가까운 자리에서만 일시적 실패를 재시도
```

이 순서를 코드가 아니라 말로 설명하면 그럴듯하지만, `PaymentProcessingServiceIntegrationTest`가 실제로 검증하는 건 순서 값 자체가 아니라 그 순서가 만들어내는 **관찰 가능한 행동**이다:

- `repeatedProcessPaymentWithSameIdempotencyKeyDoesNotChargeTwice` — 같은 멱등성 키로 두 번 호출해도 `PaymentResult.transactionId()`(내부적으로 `System.nanoTime()`을 포함)가 동일하다는 것으로 "두 번째 호출이 실제 게이트웨이까지 도달하지 않았다"를 간접 증명한다. 반면 `TimingLog`/`AuditLog`는 호출마다(캐시 히트여도) 기록이 남는다 — Timing/Audit이 Idempotency보다 바깥쪽에 있기 때문에 나오는, 처음엔 직관적이지 않았던 결과.
- `refundRequiresAdminRoleAndDeniedAttemptIsStillAudited` — 권한 거부(`AccessDeniedException`)도 감사 로그에 실패로 정확히 남는다 — Auth가 Audit보다 안쪽이라 그 예외가 Audit을 그대로 통과해 올라가기 때문.
- `pingProviderRecoversFromTransientFailuresViaRetry` / `pingProviderGivesUpWhenFailuresExceedMaxAttempts` — Retry가 `maxAttempts` 안에서 정확히 마지막 시도에 성공하면 예외 없이 반환하고, 다 소진하면 마지막 실패를 그대로 다시 던진다. `TimingLog`는 재시도 3번을 포함해도 딱 1건만 남는다 — Timing이 Retry 바깥쪽이라는 증거.

### 5.2 IdempotencyStore와 실패의 관계

`IdempotencyStore#computeIfAbsent`는 `ConcurrentHashMap#computeIfAbsent`를 그대로 쓴다 — 이 메서드는 "supplier가 예외를 던지면 아무것도 캐시하지 않는다"는 계약을 갖고 있다(자바 표준 라이브러리 문서에 명시됨). 그 덕분에 `IdempotencyAspect`를 따로 구현하지 않고도 "실패한 시도는 멱등성 캐시에 남지 않고, 같은 키로 다시 호출하면 처음부터(재시도 포함) 다시 실행된다"가 공짜로 보장된다 — 프로젝트 23(트랜잭셔널 아웃박스)의 "성공한 것만 멱등하게 재사용한다"는 원칙과 같은 결의 설계다.

### 5.3 예상과 달랐던 것 — BeanPostProcessor 조기 생성과 AOP 프록시의 실제 경계

Phase 1(§4.3)에서 `PluginRegistrationBeanPostProcessor`가 `PluginCatalog`를 조기 생성시킨다고 적었을 때는 이게 실제로 문제가 되는 걸 본 적이 없어서 추측이었다. Phase 2에서 실제로 확인했다: `PaymentProcessingService`는 `@Timed`/`@Audited`/`@RequiresRole`/`@IdempotencyGuarded`/`@Retryable` 중 하나라도 걸린 메서드가 있어서 AOP 프록시 대상이 되지만, **`PluginRegistrationBeanPostProcessor` 자신이나 `PluginCatalog`, `TimingLog`, `AuditLog`, `IdempotencyStore` 같은 인프라 빈에는 어떤 AOP 애노테이션도 걸지 않았다** — 그래서 이번 Phase에서는 실제로 조기 생성 vs 프록시 우회 충돌이 발생하지 않았다. 즉, "위험한 조합"(BPP가 의존하는 빈에 AOP 어드바이스가 걸려 있는 경우)을 코드로 직접 재현하지는 못했다 — 이건 의도적으로 인프라 빈들을 AOP 대상에서 제외했기 때문이며, 이 경계를 직접 깨 보는 실험은 이 문서의 범위 밖으로 남겨 둔다(궁금하면 `PluginCatalog`에 `@Timed`를 걸어 `PluginRegistrationBeanPostProcessor`가 원본 인스턴스를 받는지 프록시를 받는지 확인해 볼 수 있다).

### 5.4 테스트

[`AopAspectUnitTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/aop/AopAspectUnitTest.java) (7개, `AspectJProxyFactory`로 어드바이스 하나씩만 격리해서 검증):

| 테스트 | 확인하는 것 |
| --- | --- |
| `timingAspectRecordsElapsedTimeAroundTheCall` | 정상 흐름 — 실행 시간이 기록됨 |
| `authorizationAspectAllowsMatchingRoleAndDeniesOthers` | 역할이 맞으면 통과, 다르면 `AccessDeniedException` |
| `auditAspectRecordsBothSuccessAndFailureExactlyOnce` | 성공/실패 각각 정확히 한 번씩만 기록되고, 실패는 원래 예외를 그대로 다시 던짐 |
| `idempotencyAspectSkipsReExecutionForARepeatedKey` | 같은 키 재호출은 실제 실행을 건너뜀, 다른 키는 실행됨 |
| `retryAspectRetriesUntilSuccessWithinMaxAttempts` | 경계 조건 — 마지막 시도에서 성공하면 그 결과를 반환 |
| `retryAspectGivesUpAfterMaxAttemptsAndRethrowsTheLastFailure` | 경계 조건 — `maxAttempts`를 넘기면 마지막 실패를 다시 던짐 |
| `retryAspectDoesNotRetryAnExceptionTypeItWasNotConfiguredFor` | 경계 조건 — `retryFor()`에 해당하지 않는 예외는 재시도 없이 즉시 전파 |

[`PaymentProcessingServiceIntegrationTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/payment/PaymentProcessingServiceIntegrationTest.java) (4개, 실제 `AnnotationConfigApplicationContext` + `@EnableAspectJAutoProxy` 기반 — 여러 어드바이스가 실제로 겹쳤을 때의 동작만 다룸): §5.1에 기록한 4가지 시나리오.

## 6. Phase 3 — 트랜잭션

Order/PaymentHistory/OrderOutboxEvent를 `JdbcTemplate` + 임베디드 H2로 영속화하고(23주차 트랜잭셔널 아웃박스와 같은 스택), `OrderPlacementService.placeOrder()` 하나의 트랜잭션 경계 안에서 세 가지 상반된 요구를 동시에 만족시켰다: 주문+Outbox는 원자적으로 묶여야 하고, 결제 시도 이력은 그 주문 트랜잭션이 롤백돼도 살아남아야 하고, 실제 결제는 Phase 2의 AOP 스택(`PaymentProcessingService`)을 그대로 통과해야 한다.

### 6.1 결제 이력을 `REQUIRES_NEW`로 분리해야 하는 이유

`PaymentHistoryRecorder.attemptAndRecord()`는 `OrderPlacementService.placeOrder()`와 다른 트랜잭션(`REQUIRES_NEW`)에서 실행된다. `aFailedPaymentRollsBackTheOrderButThePaymentHistoryRecordSurvives` 테스트가 정확히 이걸 확인한다 - 결제가 거절되면(`PointPaymentGateway`가 `amountWon<=0`일 때 예외 없이 `success=false`를 반환하는 정상 실패 경로) `placeOrder()`는 `PaymentFailedException`을 던지며 롤백되어 `orders` 테이블에 그 주문이 아예 남지 않지만, 이미 독립적으로 커밋된 `payment_history` 행(`success=false`)은 그대로 남아 있다.

**직접 확인한 함정**: 처음엔 `PaymentHistoryRecorder`를 `OrderPlacementService`의 private 메서드로 넣으려고 했다. 그랬다면 `this.attemptAndRecord(...)` 형태의 self-invocation이 되어 Spring의 프록시 기반 AOP를 완전히 건너뛰고, `@Transactional(REQUIRES_NEW)`가 전혀 적용되지 않았을 것이다(21주차 Transaction Propagation Playground에서 이미 알고 있던 함정이지만, 이번에 실제로 새 클래스로 분리하지 않고 진행했다가 재현해 볼 뻔했다) - 그래서 반드시 별도 빈으로 분리했다.

### 6.2 주문+Outbox는 왜 자동으로 원자적인가

`placeOrder()`가 성공 경로를 타면 `orderRepository.updateStatus(PAID)`와 `outboxRepository.save(...)`가 같은 메서드, 같은 트랜잭션(기본 `REQUIRED`) 안에서 실행된다 - 둘 다 같은 `JdbcTemplate`/같은 `DataSource` 커넥션을 쓰므로 23주차에서 이미 검증한 로컬 트랜잭션의 원자성을 그대로 물려받는다. 새로운 메커니즘이 아니라 "같은 트랜잭션 경계 안에 두 개의 쓰기를 넣었을 뿐"이라는 걸 `placingAnOrderWithSuccessfulPaymentCommitsOrderPaymentHistoryAndOutboxTogether` 테스트로 다시 한 번 확인했다.

### 6.3 rollback-only 실험 — 예외를 삼켜도 소용없다

`OrderCancellationService.cancelSwallowingValidationFailure()`는 `OrderValidator.assertCancellable()`이 던진 `OrderNotCancellableException`을 `catch`해서 정상적으로(`CancellationOutcome.VALIDATION_FAILED_BUT_SWALLOWED`) 반환하려 한다. 하지만 `assertCancellable()`이 `@Transactional`(기본 `REQUIRED`)로 **호출자의 트랜잭션에 참여**했기 때문에, 그 안에서 예외가 발생한 순간 `AbstractPlatformTransactionManager`가 현재 트랜잭션을 이미 rollback-only로 표시해 버린다 - 애플리케이션 코드가 그 예외를 잡아서 삼켰다는 사실은 트랜잭션 매니저 입장에서 아무 의미가 없다. 그래서 `cancelSwallowingValidationFailure()`가 정상적으로 `return`해도, 바깥의 `@Transactional` 어드바이스가 커밋을 시도하는 순간 `UnexpectedRollbackException`이 대신 던져진다 - 메서드가 반환하려던 값(`CancellationOutcome`)은 호출자에게 전혀 도달하지 못한다.

`swallowingAnInnerRequiredValidationFailureStillMarksTheOuterTransactionRollbackOnly` 테스트가 이걸 그대로 재현한다. 대조군으로 `cancellingAPendingOrderSucceeds`(검증을 통과하는 정상 흐름)도 함께 뒀다 - "왜 이 실험이 놀라운가"는 실패 케이스만으로는 안 보이고, 정상 케이스와 나란히 놓아야 "검증 실패가 생기는 순간부터 다르게 동작한다"는 게 드러난다.

### 6.4 테스트

[`OrderPlacementServiceTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/order/OrderPlacementServiceTest.java) (4개, 실제 `AnnotationConfigApplicationContext` + 임베디드 H2 기반):

| 테스트 | 확인하는 것 |
| --- | --- |
| `placingAnOrderWithSuccessfulPaymentCommitsOrderPaymentHistoryAndOutboxTogether` | 정상 흐름 — 주문 PAID + 결제 이력 + Outbox 이벤트가 모두 같은 트랜잭션으로 커밋됨 |
| `aFailedPaymentRollsBackTheOrderButThePaymentHistoryRecordSurvives` | 경계 조건 — 결제 거절 시 주문/Outbox는 롤백되지만 `REQUIRES_NEW`로 분리된 결제 이력은 살아남음 |
| `cancellingAPendingOrderSucceeds` | 정상 흐름 — 검증을 통과하면 취소가 실제로 반영됨(6.3의 대조군) |
| `swallowingAnInnerRequiredValidationFailureStillMarksTheOuterTransactionRollbackOnly` | 경계 조건 — 내부 REQUIRED 예외를 삼켜도 트랜잭션은 이미 rollback-only, 결국 `UnexpectedRollbackException` |

## 7. Phase 4 — MVC

`OrderPlatformConfig`(Phase 1~3, 순수 `AnnotationConfigApplicationContext`)와 `OrderWebConfig`(Phase 4, `@EnableWebMvc` + `AnnotationConfigWebApplicationContext`)를 별도 설정 클래스로 분리했다 - `OrderPlatformConfig`의 `@ComponentScan`에서 `web` 패키지를 명시적으로 제외해서, 웹이 필요 없는 기존 테스트는 전혀 건드리지 않고 MVC 계층을 얹었다(project 25/26이 이미 확립해 둔, MVC 설정을 좁게 스캔하는 전례를 그대로 따른 것).

### 7.1 `@CurrentMember` — ArgumentResolver가 인증 컨텍스트를 옆으로 흘려보내는 지점

`CurrentMemberArgumentResolver`는 `X-Member-Id`/`X-Member-Role` 헤더를 `CurrentActor.Actor`로 해석해서 컨트롤러 파라미터에 바인딩하는 동시에, 같은 값을 Phase 2의 `CurrentActor`(AuditAspect/AuthorizationAspect가 읽는 ThreadLocal)에도 심어 둔다 - 지금까지 테스트가 직접 `currentActor.set(...)`을 호출해 주던 걸, 이제는 실제 요청이 들어오면 이 리졸버가 대신 해 준다. 실제 Spring Security는 DispatcherServlet보다 앞선 Filter(SecurityContextPersistenceFilter류)가 이 일을 하지만, 여기서는 별도 필터 계층을 만들지 않고 ArgumentResolver 시점에 채운다 - 그 결과 "이 리졸버가 실행되기 전"(예: HandlerInterceptor#preHandle)에는 아직 CurrentActor가 비어 있다는 제약이 생긴다. `PaymentController.refund()`는 `actor` 파라미터 값 자체를 쓰지 않지만 그래도 `@CurrentMember`를 선언해 둬야 한다 - 그래야 이 리졸버가 실행돼서 Phase 2의 `@RequiresRole(ADMIN)` 검사가 볼 CurrentActor가 채워지기 때문이다.

**ThreadLocal 유출 방지**: 서블릿 컨테이너는 요청마다 스레드를 새로 만들지 않고 스레드 풀을 재사용하므로, `CurrentActor`를 지우지 않으면 다음 요청이 (자신은 인증 헤더를 보내지 않았는데도) 이전 요청의 액터를 그대로 이어받을 수 있다. `CurrentActorClearingInterceptor#afterCompletion`이 매 요청 끝에 정리한다 - `currentActorDoesNotLeakFromOneRequestToTheNextOnTheSameThread` 테스트가 이 인터셉터를 빼면 실제로 깨지는지까지 확인했다.

### 7.2 커스텀 `Converter` — 예상과 실제가 갈린 지점

원래 계획은 `Converter<String, PaymentMethod>`를 등록해서, `BANK_TRANSFER`(Phase 1에서 일부러 어떤 게이트웨이도 구현하지 않은 값)를 서비스 계층까지 내려보내지 않고 웹 계층에서 400으로 거절하는 것이었다. `GenericConversionService`만 단독으로 테스트하면 이 계획대로 동작한다 - 더 구체적인 `(String, PaymentMethod)` 등록이 `StringToEnumConverterFactory`의 `(String, Enum)` 등록보다 우선한다.

**직접 겪은 함정**: 그런데 실제 `@RequestParam` 바인딩 경로(MockMvc로 재현)에서는 이 Converter가 거절해도 요청이 그냥 통과했다 - `PaymentGatewayRegistry.charge()`까지 내려가서야 `IllegalArgumentException`으로 실패했다. 원인은 `TypeConverterDelegate#convertIfNecessary()`에 있다: `ConversionService`가 예외를 던지면 그 자리에서 바로 전파하지 않고 일단 붙잡아 두고, "대상 타입이 Enum이고 값이 String이면 `Enum#valueOf()`로 한 번 더 시도한다"는 오래된(ConversionService보다 먼저부터 있던) 하위 호환 fallback을 마지막에 실행한다 - 그 fallback이 `BANK_TRANSFER`를 조용히 성공시켜 버려서 우리 Converter의 거절이 통째로 무시된 것이다. 그래서 대상 타입을 `PaymentMethod`(enum)가 아니라 `PaymentMethodParam`(이 fallback이 적용될 수 없는, 순수 웹 계층 래퍼 타입)로 바꿨다 - **enum을 `@RequestParam`/`@PathVariable` 타입으로 직접 쓰면, 아무리 구체적인 Converter를 등록해도 그 Converter로 요청을 "거절"하는 건 근본적으로 불가능하다**는 게 이번에 확인한, 일반화되는 결론이다.

### 7.3 공통 응답 처리 — 타입은 재사용하되 정책은 새로 짰다

`project 26`(`spring-extensions/api-response-handler`)의 `ApiResponse<T>` 레코드를 의존성으로 추가해 그대로 재사용했다. 하지만 그 모듈의 `ApiResponseBodyAdvice`는 그대로 쓰지 않았다 - `ApiResponse.of()`가 `success`를 항상 `true`로 고정하기 때문에(그 모듈은 성공 응답만 다뤘다), 그 advice를 그대로 썼다면 `@ExceptionHandler`가 돌려주는 에러 응답도 `"success": true`로 나갔을 것이다. `OrderResponseBodyAdvice`는 본문이 `ErrorResponse`인지 여부로 `success`를 직접 계산한다 - "타입은 재사용하고 그 타입을 감싸는 정책은 이 모듈의 요구에 맞게 새로 짠다"는 선택.

### 7.4 예외 처리 — 왜 project 25의 패턴을 그대로 따르지 않았는가

`spring-extensions/current-user-argument-resolver`의 `MissingCurrentUserException`은 `ResponseStatusException`을 상속해서 Spring이 자동으로 상태 코드를 매핑하게 한다. 이 모듈에서는 그 패턴을 의도적으로 쓰지 않았다 - `ResponseStatusException` 경로(`ResponseStatusExceptionResolver`)는 `response.sendError()`로 끝나서 메시지 컨버터를 아예 거치지 않고, 그러면 `OrderResponseBodyAdvice`도 적용되지 않아 그 응답만 `ApiResponse` 봉투를 벗어난다. 그래서 `MissingCurrentMemberException`은 평범한 `RuntimeException`으로 두고 `OrderExceptionHandlers`가 다른 에러들과 같은 경로로 처리한다. `OrderExceptionHandlers` 하나가 Phase 2(`AccessDeniedException`)와 Phase 3(`PaymentFailedException`/`OrderNotCancellableException`/`UnexpectedRollbackException`)의 예외까지 전부 여기서 HTTP 상태를 얻는다 - 각 예외는 원래 자기 Phase의 목적만 신경 쓰면 됐고, "HTTP로 나갈 때 몇 번이어야 하는가"는 이 웹 계층의 관심사로 완전히 분리돼 있다.

### 7.5 테스트

[`OrderWebIntegrationTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/web/OrderWebIntegrationTest.java) (5개), [`PaymentMethodConverterTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/web/PaymentMethodConverterTest.java) (3개) — `AnnotationConfigWebApplicationContext` + `MockMvc` 기반:

| 테스트 | 확인하는 것 |
| --- | --- |
| `placingAnOrderResolvesTheCurrentMemberAndWrapsTheSuccessResponse` | 정상 흐름 — 인증 헤더 → 주문 생성 → `ApiResponse` 봉투로 응답 |
| `placingAnOrderWithoutAMemberHeaderIsRejectedBeforeReachingTheService` | 경계 조건 — 인증 없으면 401, 에러도 같은 봉투 |
| `fetchingAMissingOrderReturnsAWrappedErrorResponse` | 경계 조건 — 존재하지 않는 주문 조회 시 404 |
| `refundRequiresAdminRoleEvenThoughTheControllerLayerDoesNotCheckItItself` | Phase 2 AOP와의 통합 — 컨트롤러는 권한을 검사하지 않는데도 CUSTOMER는 403, ADMIN은 성공 |
| `currentActorDoesNotLeakFromOneRequestToTheNextOnTheSameThread` | 경계 조건 — 인터셉터가 없으면 실패했을 ThreadLocal 유출 시나리오 |
| `aSupportedMethodConvertsAndTheRequestSucceeds` | 정상 흐름 — CARD/POINT는 정상 변환 |
| `anEnumConstantThatNoGatewaySupportsIsRejectedAtTheWebLayerWithAConsistentErrorEnvelope` | 경계 조건 — `BANK_TRANSFER`가 실제로 400으로 거절됨(7.2의 결론을 그대로 검증) |
| `aCompletelyInvalidEnumValueAlsoGetsAConsistentErrorEnvelope` | 경계 조건 — enum에 아예 없는 값도 같은 봉투로 실패 |

**직접 겪은 실수**: 처음 두 테스트 클래스를 작성할 때 `@AfterEach`에서 `context.close()`를 빼먹었다. `JdbcConfig`의 `EmbeddedDatabaseBuilder`는 이름을 지정하지 않으면 항상 같은 기본 이름("testdb")을 쓰는데, 컨텍스트를 닫지 않고 다음 테스트로 넘어가면 이전 테스트의 인메모리 DB가 여전히 살아 있는 채로 다음 컨텍스트가 같은 이름으로 `schema.sql`을 다시 실행하려다가 "테이블이 이미 있다"는 SQL 문법 오류로 깨졌다 - `OrderPlacementServiceTest`(Phase 3)는 처음부터 `@AfterEach`를 갖추고 있어서 이 문제를 겪지 않았던 것뿐이었다.

## 8. Phase 5 — 이벤트

`OrderPlacementService.placeOrder()`는 주문이 `PAID`가 되고 Outbox 행을 저장한 직후(같은 트랜잭션 안에서) `OrderCompletedEvent`를 발행한다. 이 이벤트를 구독하는 `NotificationDispatchListener`와 `OutboxPublishListener`는 둘 다 `@TransactionalEventListener(phase = AFTER_COMMIT)`이다 - 21주차 애플리케이션 이벤트 문서에서 이미 소스로 확인한 메커니즘(`TransactionalApplicationListenerMethodAdapter`가 즉시 실행하는 대신 현재 트랜잭션의 동기화 콜백으로 등록해 둔다)을 Order 도메인 위에서 실제로 조립한 것이다.

### 8.1 왜 "발행"은 이벤트를 거치고, "저장"은 거치지 않는가

Outbox 행 저장(`outboxRepository.save(...)`)은 여전히 Phase 3처럼 `placeOrder()`의 트랜잭션 안에서 직접 호출한다 - 이건 주문 상태 변경과 원자적으로 묶여야 하는 로컬 DB 쓰기이지, "트랜잭션 밖의 시스템에 알려야 하는 일"이 아니기 때문이다. 반면 Outbox 행을 실제로 브로커에 **발행**하는 것과 회원에게 **알림을 보내는** 것은 둘 다 트랜잭션 밖의 부작용이고, 그 트랜잭션이 실제로 커밋됐는지 확인한 뒤에만 일어나야 한다 - 그래서 이 둘만 이벤트/`AFTER_COMMIT`을 거친다. "저장은 트랜잭션 안에서 직접, 부작용은 이벤트로 커밋 이후에"라는 이 구분이 이번 Phase가 실제로 보여주는 설계 원칙이다.

### 8.2 직접 확인한 것 — AFTER_COMMIT은 정말 "미뤄질 뿐" 동기적이다

`placingAnOrderDispatchesNotificationsAndPublishesTheOutboxEventAfterCommit` 테스트는 `service.placeOrder(...)` 호출이 **반환한 시점에** 이미 알림 발송과 Outbox 발행 시도까지 전부 끝나 있다고 가정하고 별도 대기 없이 바로 단언한다 - 그리고 실제로 통과한다. `AFTER_COMMIT` 콜백은 별도 스레드나 비동기 큐가 아니라, 트랜잭션 커밋 처리 자체의 마지막 단계로 호출자에게 제어가 돌아가기 전에 동기적으로 실행된다는 걸 이번에도 재확인했다(21주차 문서의 결론과 동일).

### 8.3 rollback 시 리스너가 아예 실행되지 않음을 직접 재현

`aRolledBackTransactionNeverRunsTheAfterCommitListeners` 테스트는 `OrderPlacementService`를 거치지 않고 `TransactionTemplate`로 직접 트랜잭션을 열어 `OrderCompletedEvent`를 발행한 뒤 예외를 던져 강제로 롤백시킨다 - 그 결과 `SentNotificationLog`/`OrderOutboxRepository` 둘 다 아무 흔적도 남지 않는다. `placeOrder()`의 실제 결제-실패 경로는 애초에 이벤트 발행 이전에 예외를 던지므로(주문 저장 자체가 롤백된다) 이 시나리오를 자연스럽게 재현하지 못한다 - 그래서 이 테스트만 별도로 트랜잭션을 직접 다룬다.

**직접 겪은 것**: 처음엔 `ctx.getBean(ApplicationEventPublisher.class)`로 퍼블리셔를 얻으려다 `NoSuchBeanDefinitionException`을 봤다 - `ApplicationEventPublisher`는 컨테이너가 별도 빈으로 등록해 두는 게 아니라 `ApplicationContext` 자신이 구현하는 인터페이스라, 컨텍스트 참조를 그 타입으로 바로 쓰면 된다.

### 8.4 Outbox 발행 실패는 여전히 유실을 의미하지 않는다

`aBrokerFailureLeavesTheOutboxEventUnpublishedForTheNextPoll` 테스트는 `FakeOrderEventBroker.failNextSend()`로 커밋 직후의 발행 시도를 실패시킨다 - `OrderOutboxPublisher.publishPending()`이 그 예외를 안에서 잡아 두기 때문에 `placeOrder()`는 정상적으로 반환하고, 다만 그 Outbox 행은 `published=false`로 남는다. 이후 `publishPending()`을 다시 호출(실제 운영이라면 스케줄러의 다음 폴링)하면 그제서야 발행되고 `published=true`가 된다 - 23주차 아웃박스 문서에서 정리한 "발행 실패는 재시도할 수 있는 상태를 보장할 뿐, 실패 자체를 막아 주지 않는다"는 결론을 그대로 재사용한 것이다.

### 8.5 테스트

[`OrderCompletionEventTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/event/OrderCompletionEventTest.java) (3개, 실제 `AnnotationConfigApplicationContext` 기반):

| 테스트 | 확인하는 것 |
| --- | --- |
| `placingAnOrderDispatchesNotificationsAndPublishesTheOutboxEventAfterCommit` | 정상 흐름 — 알림(email+sms) 발송, Outbox 발행이 커밋 직후 동기적으로 끝남 |
| `aRolledBackTransactionNeverRunsTheAfterCommitListeners` | 경계 조건 — 롤백되면 AFTER_COMMIT 리스너가 아예 실행되지 않음 |
| `aBrokerFailureLeavesTheOutboxEventUnpublishedForTheNextPoll` | 경계 조건 — 발행 실패는 유실이 아니라 재시도 가능한 상태로 남음 |

**기존 테스트 업데이트**: Phase 3에서 작성한 `OrderPlacementServiceTest`의 `placingAnOrderWithSuccessfulPaymentCommitsOrderPaymentHistoryAndOutboxTogether`가 이번에 실패했다 - Phase 3 시점엔 Outbox "저장"까지만 있어서 항상 `published=false`였는데, Phase 5가 발행 단계를 추가하면서 그 단언이 틀린 게 됐다. `!event.published()`를 `event.published()`로 고쳤다 - 새 기능이 이전 Phase의 가정을 깨뜨린 사례를 실제로 겪은 것.

## 9. Phase 6 — Boot (마지막 Phase)

Phase 1의 `PaymentGatewayClient`와 `NotificationDispatcher`에서 `@Component`를 떼고, Boot 스타일 `@AutoConfiguration` + `@ConfigurationProperties`로 등록 방식을 옮겼다. 그리고 이 모듈을 실제로 `SpringApplication`으로 띄울 수 있는 `OrderPlatformApplication`을 추가해서, 손댄 적 없는 `spring-extensions/mini-observability-starter`의 요청 관측 인터셉터가 우리 MVC 파이프라인에 자동으로 꽂히는 것까지 실제 HTTP 요청으로 검증했다.

### 9.1 왜 `@ConditionalOnBean`을 쓰지 않았는가

`NotificationAutoConfiguration`을 설계할 때 처음 든 생각은 "채널이 하나도 없으면 Dispatcher도 만들지 말자"는 의미로 `@ConditionalOnBean(NotificationChannel.class)`를 쓰는 것이었다. 하지만 Boot 공식 문서가 명시적으로 경고하는 함정이 있다 - `@ConditionalOnBean`은 대상 자동 설정이 `AutoConfigurationImportSelector`의 지연(deferred) 처리 경로를 거칠 때만 순서가 보장되고, 이 모듈처럼 컴포넌트 스캔이나 평범한 `@Import`로 가져오면 다른 설정 클래스가 아직 다 처리되지 않은 시점에 조건이 평가될 수 있다. 그래서 대신 `List<NotificationChannel>`을 `@Bean` 팩토리 메서드의 파라미터로 받는 방식을 썼다 - 조건 평가 시점이 아니라 실제 빈 생성 시점에 해석되므로 순서에 영향받지 않는다(0개여도 빈 리스트가 주입될 뿐 실패하지 않는다). `channelsDeclaredByUserConfigurationAreInjectedRegardlessOfImportOrder` 테스트가 이 선택이 실제로 안전하다는 것까지 확인한다.

### 9.2 직접 겪은 가장 큰 함정 — 컴포넌트 스캔에 우연히 휩쓸린 `@EnableAutoConfiguration`

`OrderPlatformApplication`(진입점, `@EnableAutoConfiguration`)을 다른 Boot 관련 클래스들과 같은 `boot` 패키지에 뒀다. 그런데 `OrderPlatformConfig`/`OrderWebConfig` 둘 다 `"lab.sampleapp.orderplatform"` 전체를 컴포넌트 스캔하고 있었다 - `boot` 패키지도 예외가 아니었다. 그 결과, Phase 1~5의 순수 `AnnotationConfigApplicationContext` 테스트들이 `OrderPlatformApplication` 자신을 평범한 `@Configuration` 후보로 주워 담아 버렸고, 그 클래스에 붙은 `@EnableAutoConfiguration`은 **컴포넌트 스캔으로 "발견되기만 해도" 그대로 활성화**됐다 - Spring Boot의 표준 `DataSourceAutoConfiguration`, `SqlInitializationAutoConfiguration` 등 전부가 이 순수 테스트 컨텍스트 안으로 끌려들어와서, `JdbcConfig`가 `EmbeddedDatabaseBuilder#addScript()`로 이미 실행해 둔 `schema.sql`을 Boot가 또 한 번 실행하려다가 "테이블이 이미 있다"는 오류로 깨졌다.

증상 자체는 낯익었다 - Phase 4에서 이미 겪은 "H2 `testdb` 기본 이름 충돌"과 똑같은 오류 메시지였다. 그래서 처음엔 또 `@AfterEach`에서 `context.close()`를 빼먹은 줄 알았는데, 이번엔 **매 테스트가 첫 실행부터** 실패하고 있었다 - 즉 컨텍스트 간 누수가 아니라 **단일 컨텍스트 안에서 같은 스키마가 두 번** 실행되고 있다는 신호였다. `getBeanDefinitionNames()`로 실패한 컨텍스트의 빈 정의를 직접 덤프해 보고서야 `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`, `org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration` 같은 Boot 표준 클래스들이 버젓이 등록돼 있는 걸 확인했다 - `OrderPlatformConfig`에는 `@EnableAutoConfiguration`이 전혀 없는데도.

**교훈**: `@SpringBootApplication`/`@EnableAutoConfiguration`이 붙은 진입점 클래스는 자신이 루트가 되는 스캔에만 등장해야 한다 - 다른 목적의(특히 그 진입점을 몰라도 되는) 컴포넌트 스캔에 우연히 휩쓸리면, 그 스캔이 의도하지 않았던 Boot의 전체 자동 설정 표면을 통째로 활성화시켜 버린다. 고친 방법은 `OrderPlatformConfig`/`OrderWebConfig` 양쪽의 `@ComponentScan`에 `excludeFilters`로 `OrderPlatformApplication.class`를 `ASSIGNABLE_TYPE`으로 명시적으로 제외하는 것이었다.

### 9.3 두 번째로 겪은 함정 — 테스트 픽스처도 같은 스캔에 휩쓸린다

위 문제를 고친 뒤에도 실패가 남아 있었다: `MiniOrderPlatformIoCLifecycleTest`의 `PluginCatalog`에 존재해서는 안 될 `"fakeChannel"` 항목이 나타났고, `NotificationDispatcher`가 채널을 하나도 못 찾는 테스트도 있었다. 원인은 같은 종류의 실수였다 - project 32의 `RequestObservationAutoConfigurationTest`를 그대로 따라 `withUserConfiguration(UserDispatcherConfig.class)`처럼 **중첩 `@Configuration` 테스트 픽스처 클래스**를 만들었는데, 그 테스트 클래스 자신이 `lab.sampleapp.orderplatform.boot` 패키지(스캔 대상 트리 안)에 있었다 - project 32에서는 그 테스트가 독립된 다른 모듈/패키지에 있어서 전혀 문제가 없었지만, 이 모듈은 자기 자신의 테스트 소스셋이 프로덕션 스캔 루트와 같은 패키지 트리를 공유한다. `OutboxSampleConfig`(23주차)가 진작에 "패키지 전체를 스캔하면 테스트 소스셋의 헬퍼 클래스까지 함께 주워 담을 위험이 있다"고 남겨 둔 경고를 이번에 직접 재현한 셈이다.

고친 방법: 새 스캔 가능한 클래스를 만드는 `withUserConfiguration(...)` 대신, `ApplicationContextRunner#withBean(Class, Supplier)`로 빈을 직접 등록했다 - 컴포넌트 스캔이 절대 발견할 수 없는, 익명의 인메모리 빈 등록이라 이 위험 자체가 원천적으로 없다.

### 9.4 세 번째 함정 — 진짜 Boot 부트스트랩에서는 스키마 초기화가 실제로 두 번 겹친다

9.2의 수정으로 Phase 1~5 테스트는 다시 통과했지만, `OrderPlatformApplicationEndToEndTest`(진짜 `@EnableAutoConfiguration` 경로를 쓰는 유일한 테스트)는 여전히 같은 "테이블이 이미 있다" 오류로 실패했다 - 이번엔 우연한 스캔 오염이 아니라 **의도한 대로 `@EnableAutoConfiguration`이 정상적으로 동작한 결과**였다. Boot의 `SqlInitializationAutoConfiguration`은 클래스패스에서 `schema.sql`을 자동으로 찾아 실행해 주는데, `JdbcConfig#dataSource()`가 `EmbeddedDatabaseBuilder#addScript()`로 이미 그 파일을 실행해 둔 `DataSource` 빈을 그대로 재사용하다 보니(`@ConditionalOnMissingBean` 덕분에 Boot가 새 `DataSource`를 만들지는 않는다), 같은 스크립트가 같은 데이터소스에 두 번 실행되는 것 자체가 문제였다. `OrderPlatformApplication`의 `@EnableAutoConfiguration(exclude = SqlInitializationAutoConfiguration.class)`로 Boot의 자동 스키마 초기화를 명시적으로 껐다 - "우리가 이미 끝낸 일을 Boot가 또 하려고 한다"는 걸 알아차리고 그 중복만 정확히 제거한 것이다.

### 9.5 요청 관측 Starter — 배선 코드 없이 실제로 작동하는 것을 확인

`OrderPlatformApplicationEndToEndTest`가 이 캡스톤 전체의 결승점 격이다: `OrderPlatformApplication`을 `@EnableAutoConfiguration`으로 띄우고, `MockMvc`로 `/orders/999999`에 실제 HTTP 요청을 보낸 뒤, `spring-extensions/mini-observability-starter`의 `ObservationLog`에 그 경로가 기록됐는지 확인한다. 이 모듈의 코드 어디에도 `RequestObservationInterceptor`를 등록하는 코드가 없다 - `mini-observability-starter:starter` 의존성 하나와 `@EnableAutoConfiguration` 하나로, 그 스타터의 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`가 자동으로 발견되고, `WebMvcConfigurer` 빈으로 등록된 인터셉터가 `OrderWebConfig`의 `@EnableWebMvc` MVC 파이프라인(우리가 만든 것)에 자연스럽게 합류했다.

### 9.6 테스트

[`PaymentGatewayAutoConfigurationTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/boot/PaymentGatewayAutoConfigurationTest.java) (4개), [`NotificationAutoConfigurationTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/boot/NotificationAutoConfigurationTest.java) (4개) — `ApplicationContextRunner` + `AutoConfigurations.of(...)` 기반, project 32와 같은 관점(기본 설정/비활성화/사용자 정의 빈 우선/프로퍼티 바인딩)에 채널 주입 순서 안전성까지 추가:

| 테스트 | 확인하는 것 |
| --- | --- |
| `defaultConfigurationRegistersThePaymentGatewayClient` / `...TheDispatcherEvenWithNoChannelsPresent` | 정상 흐름 — 기본 설정에서 빈 생성 |
| `disablingThePropertyPreventsRegistration`(양쪽) | 경계 조건 — `enabled=false`면 등록하지 않음 |
| `userDefinedClientMakesTheAutoConfigurationBackOff` / `userDefinedDispatcherMakesTheAutoConfigurationBackOff` | 사용자 정의 빈이 있으면 자동 설정이 물러남(`@ConditionalOnMissingBean`) |
| `connectTimeoutPropertyBindsAndFlowsIntoTheCreatedClient` | 프로퍼티 바인딩 검증 |
| `channelsDeclaredByUserConfigurationAreInjectedRegardlessOfImportOrder` | 경계 조건 — `@ConditionalOnBean` 대신 파라미터 주입을 택한 설계가 실제로 순서에 안전함 |

[`OrderPlatformApplicationEndToEndTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/boot/OrderPlatformApplicationEndToEndTest.java) (1개) — 9.5에서 설명한 결승점 테스트.

## 10. 캡스톤 완료

Phase 1~6이 전부 끝났다 - IoC/생명주기(Phase 1), AOP(Phase 2), 트랜잭션(Phase 3), MVC(Phase 4), 이벤트(Phase 5), Boot(Phase 6) 순서로, 카탈로그(`docs/plan/02-project-catalog.md` 16번 절)가 요구한 21개 세부 기법을 전부 실제 동작하는 코드와 테스트로 채웠다. `sample-app/mini-order-platform` 모듈은 최종적으로 43개 테스트(전부 실제 Spring 컨테이너/DB/HTTP를 통과하는 통합 테스트, 목 없음)를 갖췄고, Phase 6에서 겪은 세 가지 함정(§9.2~9.4)은 이 캡스톤 전체를 통틀어 가장 오래 걸린, 그리고 가장 "Spring을 실제로 이해하지 못하면 못 만들 종류"의 디버깅이었다 - 개별 Phase에서는 각 개념을 하나씩 독립적으로 확인하는 것으로 충분했지만, 6개 Phase를 전부 한 애플리케이션에 합치는 순간에만 드러나는 상호작용(컴포넌트 스캔 범위와 `@EnableAutoConfiguration`의 상호작용, 테스트 픽스처와 프로덕션 스캔 루트의 공유, 우리 초기화와 Boot 초기화의 중복)이었다는 점이 바로 이 프로젝트가 "종합" 프로젝트인 이유였다.

이 절의 숫자(43개 테스트)는 카탈로그가 요구한 것을 전부 채운 시점 그대로 남겨 뒀다 - 그 뒤로 카탈로그 범위 밖의 확장(상품 도메인)을 추가했고, 그 경과는 [11번 절](#11-카탈로그-이후-확장--상품-도메인)에 정리했다.

## 11. 카탈로그 이후 확장 — 상품 도메인

카탈로그 16번 절이 이 캡스톤의 도메인으로 회원/상품/주문/결제/알림/감사 로그 여섯 개를 나열했지만, Phase 1~6이 요구한 21개 세부 기법 중 어느 것도 실제로 상품 도메인을 필요로 하지 않았다 - 그래서 §10까지 상품 없이 끝났다. 이 절은 카탈로그가 끝난 뒤 별도로 추가한 확장을 다룬다: `product` 패키지(`Product`, `ProductRepository`, `ProductService`)와, `OrderPlacementService`를 "클라이언트가 금액을 부르는" 방식에서 "상품 ID+수량만 받고 서버가 가격을 계산하는" 방식으로 다시 짠 것.

### 11.1 왜 클라이언트가 가격을 보내면 안 되는가

Phase 1~6 내내 `PlaceOrderRequest`는 `amountWon`을 그대로 받았다 - 캡스톤이 결제/트랜잭션/이벤트 메커니즘을 검증하는 데만 집중했기 때문에 "그 금액이 어디서 왔는가"는 범위 밖이었다. 상품 도메인을 도입하면서 이 결정을 다시 봐야 했다 - 실제 서비스라면 클라이언트가 가격을 마음대로 부를 수 있다는 것 자체가 보안 결함이다(요청을 조작해 100원짜리 주문으로 10만원짜리 상품을 사는 것과 같은 문제). 그래서 `OrderItemRequest`는 `productId`와 `quantity`만 담고, `OrderPlacementService.placeOrder()`가 `ProductRepository`에서 직접 가격을 조회해 합계를 계산한다 - 클라이언트가 가격을 조작할 수 있는 경로 자체가 API 설계에 없다.

### 11.2 재고 차감을 원자적으로 만들기 — "조회 후 갱신"을 쓰지 않는다

재고를 다루는 가장 흔한 실수는 "재고를 SELECT로 읽고, 자바에서 수량을 빼고, UPDATE로 다시 쓰는" 3단계 코드다 - 이 사이에 동시에 들어온 다른 트랜잭션이 끼어들면 두 트랜잭션 모두 "재고가 충분하다"고 판단하고 통과해 버릴 수 있다(트랜잭션 격리 수준에 따라 다르지만, 애플리케이션 코드 수준에서 막을 방법이 없다). `ProductRepository.decreaseStock()`은 이 3단계를 SQL 한 줄로 합친다:

```sql
UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?
```

조회와 조건 검사와 갱신이 전부 DB 엔진이 실행하는 하나의 문장 안에 있어서, 별도 락 없이도 원자적이다. 영향받은 행이 0이면(`WHERE` 조건에 걸려 아무 행도 갱신되지 않으면) 재고 부족이라는 뜻이다. `ProductRepositoryTest`가 이 세 가지를 확인한다: 충분할 때 성공, 부족할 때 실패하며 재고가 전혀 바뀌지 않음, 정확히 남은 만큼 요청하면 재고가 0이 되는 경계.

### 11.3 여러 상품 중 하나가 부족하면 — "전부 아니면 전무"

`OrderPlacementWithProductsTest.insufficientStockForOneItemRollsBackTheWholeOrderAndRestoresStockAlreadyDecremented`가 확인하는 시나리오: 주문에 상품 A(재고 충분)와 상품 B(재고 부족)가 함께 있으면, A의 재고가 먼저 차감된 뒤 B에서 `InsufficientStockException`이 던져진다. 이 예외로 `@Transactional` 메서드 전체가 롤백되므로, **이미 차감됐던 A의 재고도 함께 복구된다** - "일부 상품만 주문이 성사되는" 상태는 애초에 존재할 수 없다. 이건 새 메커니즘이 아니라 Phase 3에서 이미 검증한 로컬 트랜잭션 원자성(주문 저장+Outbox 저장이 함께 커밋/롤백되는 것과 같은 근거)이 재고 차감이라는 새로운 종류의 쓰기에도 그대로 적용된다는 걸 재확인한 것이다.

### 11.4 관리자 전용 상품 등록 — Phase 2 AOP를 새 도메인에 재사용

`ProductService.createProduct()`에 `@RequiresRole(Role.ADMIN)`/`@Audited`/`@Timed`를 그대로 붙였다 - 새 어드바이스를 만들지 않았다. `PaymentProcessingService.refund()`와 완전히 같은 패턴(컨트롤러가 서비스 빈을 직접 호출해야 프록시를 거쳐 어드바이스가 적용된다는 것 포함)이라, `ProductController.createProduct()`도 `@CurrentMember` 파라미터를 선언만 하고 값 자체는 쓰지 않는다 - 그 리졸버가 실행돼야 `CurrentActor`가 채워지기 때문이다. `ProductWebIntegrationTest`가 실제 HTTP 요청으로 CUSTOMER는 403, ADMIN은 성공하는 것까지 확인한다.

### 11.5 기존 테스트에 미친 영향

`OrderPlacementService.placeOrder()`의 시그니처가 `(memberId, method, long amountWon)`에서 `(memberId, method, List<OrderItemRequest>)`로 바뀌면서, Phase 3/4/5에서 이미 작성해 둔 테스트(`OrderPlacementServiceTest`, `OrderWebIntegrationTest`, `PaymentMethodConverterTest`, `OrderCompletionEventTest`)가 전부 컴파일조차 되지 않게 됐다. 각 테스트에 상품을 먼저 심어 두는 준비 단계를 추가하고 호출부를 고쳤다 - Phase 5가 Phase 3의 테스트 단언 하나를 깨뜨렸던 것(§8 "기존 테스트 업데이트")과 같은 종류의, "이전 Phase의 가정이 이후 확장으로 깨지는" 사례가 이번에도 반복됐다. 카탈로그 프로젝트들처럼 서로 독립된 모듈이 아니라 하나의 애플리케이션이 계속 성장하는 캡스톤이라는 이 문서 전체의 성격상, 앞으로도 이런 종류의 파급은 계속 생길 것으로 예상한다.

### 11.6 테스트

[`ProductRepositoryTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/product/ProductRepositoryTest.java) (3개), [`ProductServiceTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/product/ProductServiceTest.java) (2개), [`OrderPlacementWithProductsTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/order/OrderPlacementWithProductsTest.java) (3개), [`ProductWebIntegrationTest`](../../sample-app/mini-order-platform/src/test/java/lab/sampleapp/orderplatform/web/ProductWebIntegrationTest.java) (3개) - 총 11개 신규:

| 테스트 | 확인하는 것 |
| --- | --- |
| `decreaseStockSucceedsWhenEnoughStockIsAvailable` / `...CanBringStockExactlyToZero` | 정상 흐름 + 경계 — 재고 차감이 정확한 수량만큼, 0까지 안전하게 |
| `decreaseStockFailsAtomicallyAndLeavesStockUnchangedWhenNotEnough` | 경계 조건 — 부족하면 실패하고 재고는 그대로 |
| `placingAnOrderWithMultipleLineItemsComputesTheTotalFromProductPrices` | 정상 흐름 — 합계가 클라이언트가 아니라 Product 가격에서 계산됨 |
| `insufficientStockForOneItemRollsBackTheWholeOrderAndRestoresStockAlreadyDecremented` | 경계 조건 — 여러 상품 중 하나라도 부족하면 전체 롤백(이미 차감된 것도 복구) |
| `orderingAnUnknownProductThrowsBeforeAnyStockIsTouched` | 경계 조건 — 존재하지 않는 상품 |
| `creatingAProductAsCustomerIsDenied` / `...AsAdminSucceedsAndPersists` | Phase 2 AOP와의 통합 — 관리자만 상품을 등록할 수 있음 |
| `adminCanCreateAProductAndAnyoneCanReadItBack` / `customerCannotCreateAProduct` / `fetchingAMissingProductReturns404` | 실제 HTTP 요청 — 등록은 관리자 전용, 조회는 인증 없이 가능 |

모듈 전체 테스트는 43개에서 54개로 늘었다.
