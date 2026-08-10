# Mini Order Platform — 학습한 것을 하나의 앱으로 통합하기

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 16번 절 "종합 프로젝트 추천"에 대응하는 문서다. 지금까지의 32개 카탈로그 프로젝트가 각각 Spring 내부 구조 하나씩을 분리해서 파고들었다면, 이 프로젝트는 그렇게 배운 것들 — IoC, 빈 생명주기, AOP, 트랜잭션, MVC, 이벤트, Boot — 을 회원/상품/주문/결제/알림/감사 로그라는 하나의 도메인 위에서 서로 맞물리게 만드는 캡스톤이다.

카탈로그 자체가 이 프로젝트에는 코드 스켈레톤을 주지 않고 "적용할 학습 요소" 목록만 준다. 스켈레톤이 없다는 것 자체가 지금까지의 32개 프로젝트와 다른 지점이라, 이 문서는 처음부터 완성된 형태가 아니라 **단계(Phase)별로 진행 상황을 누적 기록**하는 방식으로 쓴다 — Phase가 끝날 때마다 이 문서의 해당 절을 채운다.

## 1. 도메인

```text
회원(Member)     - id, name, membership tier(BASIC/MEMBERSHIP)
상품(Product)    - 아직 Phase 1에는 없음 (Phase 3, MVC 단계에서 등장 예정)
주문(Order)      - 아직 Phase 1에는 없음 (Phase 3, 트랜잭션 단계에서 등장 예정)
결제(Payment)    - PaymentGateway 전략(CARD/POINT), PaymentGatewayClient(외부 PG 흉내)
알림(Notification) - NotificationChannel(email/sms), 컬렉션 주입으로 브로드캐스트
감사 로그        - 아직 없음 (AOP 단계에서 등장 예정)
```

도메인을 처음부터 다 만들지 않고 "이번 Phase가 요구하는 만큼만" 만든다 — 이 저장소 전체가 지켜온 "축소 구현" 원칙을 캡스톤 안에서도 그대로 따른 것이다. 예를 들어 Order/Product는 Phase 1(IoC/생명주기)에는 등장할 이유가 없어서 아직 없다.

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
    OrderPlatformConfig.java - @ComponentScan 진입점
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
| 트랜잭션 | 주문+Outbox / 결제 이력 분리(`REQUIRES_NEW`) / rollback-only | ⬜ Phase 3 | - |
| MVC | 커스텀 인증 Resolver / 공통 응답 / 예외 처리 / 커스텀 Converter | ⬜ Phase 4 | - |
| 이벤트 | 주문 완료 이벤트 / 알림 발송 / Outbox 발행 / 커밋 후 처리 | ⬜ Phase 5 | - |
| Boot | 결제 클라이언트 AutoConfiguration / 알림 Starter / 요청 관측 Starter | ⬜ Phase 6 | - |

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

## 6. 남은 Phase (TODO)

- **Phase 3 — 트랜잭션**: Order/Payment 도메인 도입, 주문+Outbox 저장(23주차 아웃박스 패턴 재사용), 결제 이력을 `REQUIRES_NEW`로 분리, rollback-only 실험.
- **Phase 4 — MVC**: `@CurrentMember` 커스텀 ArgumentResolver, 공통 `ApiResponse<T>` 응답(`spring-extensions/api-response-handler` 재사용), `@ControllerAdvice`, 커스텀 `Converter`.
- **Phase 5 — 이벤트**: 주문 완료 이벤트 → 알림 발송(4장에서 만든 `NotificationDispatcher` 재사용) + Outbox 발행을 `@TransactionalEventListener(AFTER_COMMIT)`으로 연결.
- **Phase 6 — Boot**: 결제 클라이언트 AutoConfiguration, 알림 플러그인 Starter, 요청 관측 Starter(`spring-extensions/mini-observability-starter` 패턴 재사용).

각 Phase는 이 저장소의 다른 프로젝트와 마찬가지로 "설계 → 확인 → 구현 → 테스트" 순으로 진행하고, 끝날 때마다 이 문서의 3번 절 표와 해당 Phase 절을 채운다.
