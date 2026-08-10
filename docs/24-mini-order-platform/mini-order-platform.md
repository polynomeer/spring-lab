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
| AOP | 실행 시간 측정 / 권한 검사 / 감사 로그 / 재시도 / 멱등성 검사 | ⬜ Phase 2 | - |
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

## 5. 남은 Phase (TODO)

- **Phase 2 — AOP**: 실행 시간 측정, 권한 검사, 감사 로그, 재시도, 멱등성 검사. `PluginCatalog`에 AOP 프록시를 적용해 4.3의 BPP 조기 생성 경계를 실제로 재현해 볼 계획.
- **Phase 3 — 트랜잭션**: Order/Payment 도메인 도입, 주문+Outbox 저장(23주차 아웃박스 패턴 재사용), 결제 이력을 `REQUIRES_NEW`로 분리, rollback-only 실험.
- **Phase 4 — MVC**: `@CurrentMember` 커스텀 ArgumentResolver, 공통 `ApiResponse<T>` 응답(`spring-extensions/api-response-handler` 재사용), `@ControllerAdvice`, 커스텀 `Converter`.
- **Phase 5 — 이벤트**: 주문 완료 이벤트 → 알림 발송(4장에서 만든 `NotificationDispatcher` 재사용) + Outbox 발행을 `@TransactionalEventListener(AFTER_COMMIT)`으로 연결.
- **Phase 6 — Boot**: 결제 클라이언트 AutoConfiguration, 알림 플러그인 Starter, 요청 관측 Starter(`spring-extensions/mini-observability-starter` 패턴 재사용).

각 Phase는 이 저장소의 다른 프로젝트와 마찬가지로 "설계 → 확인 → 구현 → 테스트" 순으로 진행하고, 끝날 때마다 이 문서의 3번 절 표와 해당 Phase 절을 채운다.
