# SpringApplication — ApplicationContext가 생기기도 전에 이벤트부터 발행하는 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 17주차(선택 과정: Spring Boot 내부, 1주차), [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 31(SpringApplication Lifecycle Inspector)에 대응하는 분석 문서다. 핵심 16주 과정을 마친 뒤 선택적으로 이어가는 Spring Boot 내부 동작 트랙의 첫 주다.

## 1. 이번 질문

- `SpringApplication.run()`은 정확히 어떤 단계를 수행하는가?
- `Environment`는 언제 생성되고, `ApplicationContext`는 언제 생성되는가 — 이 둘의 생성 시점이 다르다면 그 사이에는 무엇이 있는가?
- `ApplicationContext` 구현체(`AnnotationConfigApplicationContext`? 웹 전용 구현체?)는 어떻게 선택되는가?
- 8개의 생명주기 이벤트(`ApplicationStartingEvent` ~ `ApplicationReadyEvent`)는 왜 그 순서인가, 그리고 **일반적인 `@Component` 리스너로 전부 받을 수 있는가**?

## 2. 공식 문서 요약

- Spring Boot 레퍼런스("SpringApplication", "Application Events and Listeners")는 `SpringApplication.run()`이 `Environment` 준비 → `ApplicationContext` 생성 → 컨텍스트 준비 → `refresh()` → 후처리의 단계를 거친다고 설명하고, 각 단계 경계마다 발행되는 이벤트 8종을 순서대로 나열한다.
- 같은 문서는 "일부 리스너는 `ApplicationContext`가 완전히 준비되기 전에 실행되므로, 그 안에서 `@Bean`으로 등록할 수 없다 — `SpringApplication.addListeners(...)`나 `META-INF/spring.factories`로 등록해야 한다"고 명시한다. 정확히 왜 그런지는 이번 주 소스 확인으로 닫았다.
- `ApplicationContextFactory`에 대해서는 "웹 애플리케이션 타입에 맞는 컨텍스트를 생성하는 전략 인터페이스"라고만 설명하고, 후보가 여럿일 때의 선택 규칙까지는 다루지 않는다 — 이번 주 소스로 확인했다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Component`로 등록한 평범한 `ApplicationListener` 빈도 8개 이벤트를 전부 받을 수 있을 거라 예상했다 — **틀렸다.** `ApplicationContext` 자신이 아직 없는 시점(`ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`)의 이벤트는, 그 빈 자체가 아직 존재하지 않으므로 구조적으로 받을 수 없다.
- `ApplicationContextInitializedEvent` 시점이면 컨텍스트가 있으니 `@ComponentScan`도 이미 끝나서 빈 조회가 될 거라 예상했다 — **틀렸다.** 이 시점은 `ApplicationContextInitializer`만 적용된 직후이고, primary source(설정 클래스)의 `BeanDefinition`조차 아직 등록되지 않았다.
- `ApplicationContext` 구현체는 웹 애플리케이션 타입(`NONE`/`SERVLET`/`REACTIVE`)에 따라 `if-else`로 분기해서 고정적으로 선택될 거라 예상했다 — **틀렸다.** `SpringFactoriesLoader`로 등록된 `ApplicationContextFactory` 후보들에게 순서대로 물어보고, 아무도 응답하지 않으면 `AnnotationConfigApplicationContext`로 떨어지는 **탐색 후 폴백** 구조였다 — Boot의 자동 설정 메커니즘(18주차)과 같은 종류의 SPI다.

## 4. 최소 재현 코드

**실제 Spring Boot** — [`experiments/spring-application-lifecycle`](../../experiments/spring-application-lifecycle)
```java
SpringApplication application = new SpringApplication(LifecycleConfig.class);
application.setWebApplicationType(WebApplicationType.NONE);
LifecycleObserver observer = new LifecycleObserver();
application.addListeners(observer);   // ApplicationContext 밖에서 등록해야 초기 이벤트를 받는다

ConfigurableApplicationContext context = application.run();
```
```java
@Component
public final class BeanRegisteredObserver implements ApplicationListener<ApplicationEvent> {
    // 컴포넌트 스캔으로 등록되는 평범한 빈 - 초기 이벤트를 구조적으로 놓친다
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `SpringApplication#run` | `Environment` 준비 → `ApplicationContext` 생성 → `prepareContext` → `refresh()` → `afterRefresh` → 후처리 순서로 진행하는 최상위 진입점 |
| `SpringApplicationRunListeners`/`EventPublishingRunListener` | 각 단계 경계마다 대응하는 `SpringApplicationEvent`를 실제로 발행하는 리스너 - 내부에 **자신만의 `initialMulticaster`**를 갖고 있다 |
| `ApplicationContextFactory` | `WebApplicationType`에 맞는 `ConfigurableApplicationContext` 구현체를 생성하는 전략 인터페이스 - `SpringFactoriesLoader`로 후보를 찾음 |
| `ApplicationContextInitializer` | 컨텍스트가 생성된 직후, `refresh()`보다 먼저 적용되는 초기화 콜백 |
| `ApplicationListener` | 이번 주의 핵심 관찰 대상 - **어떻게 등록됐는지**에 따라 받을 수 있는 이벤트 범위가 달라진다 |
| (실험) `LifecycleObserver` | `SpringApplication.addListeners()`로 등록 - 8개 이벤트 전부 관찰 |
| (실험) `BeanRegisteredObserver` | `@Component`로 등록 - 초기 이벤트를 구조적으로 놓치는 것을 보여주는 대조군 |

## 6. 호출 흐름

```text
SpringApplication#run(args)
  → listeners.starting(bootstrapContext, mainApplicationClass)
      → EventPublishingRunListener#starting → initialMulticaster로 ApplicationStartingEvent 발행
        (application.getListeners()에 등록된 리스너만 여기 포함됨 - 빈은 아직 하나도 없음)
  → prepareEnvironment(...) → Environment 생성/구성 완료
      → listeners.environmentPrepared(...) → ApplicationEnvironmentPreparedEvent (initialMulticaster)
  → context = createApplicationContext()
      → this.applicationContextFactory.create(webApplicationType)
          → SpringFactoriesLoader로 등록된 ApplicationContextFactory 후보를 순서대로 시도
          → 아무도 응답 안 하면 new AnnotationConfigApplicationContext() (폴백)
  → prepareContext(bootstrapContext, context, environment, listeners, args, banner)
      → context.setEnvironment(environment)
      → applyInitializers(context)              (ApplicationContextInitializer 전부 적용)
      → listeners.contextPrepared(context)
          → ApplicationContextInitializedEvent (initialMulticaster) - 아직 BeanDefinition 없음
      → load(context, sources)                   (primary source의 BeanDefinition만 등록 - @ComponentScan 전개 아직 안 됨)
      → listeners.contextLoaded(context)
          → application.getListeners()의 각 리스너를 context.addApplicationListener()로 실제 등록
          → ApplicationPreparedEvent (initialMulticaster) - refresh() 아직 호출 전
  → refreshContext(context)                      (여기서 @ComponentScan 전개 + 싱글턴 생성, 3주차의 12단계)
  → afterRefresh(context, args)
  → listeners.started(context, timeTaken)
      → context.publishEvent(ApplicationStartedEvent)   (이제부터는 context 자신의 멀티캐스터를 통함)
      → AvailabilityChangeEvent.publish(context, LivenessState.CORRECT)
  → callRunners(context, args)                   (ApplicationRunner/CommandLineRunner)
  → listeners.ready(context, timeTaken)
      → context.publishEvent(ApplicationReadyEvent)
      → AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC)
```

`initialMulticaster`와 `context`의 멀티캐스터가 갈리는 지점, 그리고 두 종류의 리스너 등록 방식이 어떤 이벤트를 받는지를 함께 그린 다이어그램: [`diagrams/lifecycle-event-flow.md`](diagrams/lifecycle-event-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.boot.SpringApplication#run
org.springframework.boot.SpringApplication#prepareContext
org.springframework.boot.context.event.EventPublishingRunListener#contextLoaded
org.springframework.boot.context.event.EventPublishingRunListener#multicastInitialEvent
org.springframework.boot.DefaultApplicationContextFactory#create
```

## 8. 런타임 관찰

[`SpringApplicationLifecycleTest`](../../experiments/spring-application-lifecycle/src/test/java/lab/experiments/bootlifecycle/SpringApplicationLifecycleTest.java) (3개, 비-웹 `SpringApplication`):

| 실험 | 결과 |
| --- | --- |
| `ApplicationStartingEvent` | `Environment` 없음(`ConfigurableBootstrapContext`뿐) |
| `ApplicationEnvironmentPreparedEvent` | `Environment` 있음, `ApplicationContext` 없음 |
| `ApplicationContextInitializedEvent` | `ApplicationContext` 있음, 빈 조회는 **안 됨**(`@ComponentScan` 전개 전) |
| `ApplicationPreparedEvent` | `ApplicationContext` 있음, 빈 조회는 여전히 **안 됨**(`refresh()` 호출 전) |
| `ApplicationStartedEvent`/`ApplicationReadyEvent` | 빈 조회 **됨**(`refresh()` 완료) |
| `SpringApplication.addListeners()`로 등록한 리스너 | 8개 이벤트 전부 수신 |
| `@Component`로 등록한 리스너 | `ApplicationStartedEvent`/`ApplicationReadyEvent`/`AvailabilityChangeEvent`만 수신 - 앞의 4개(`Starting`/`EnvironmentPrepared`/`ContextInitialized`/`Prepared`)는 구조적으로 놓침 |
| web 관련 의존성이 전혀 없는 클래스패스에서 `WebApplicationType.NONE` | `AnnotationConfigApplicationContext`로 폴백(`isExactlyInstanceOf`로 확인) |

**미룬 것**: 카탈로그가 요구하는 "웹 서버 시작 여부" 관찰은 이번 주 범위에서 뺐다 - 실제 `ServletWebServerFactory` 빈은 자동 설정(`@EnableAutoConfiguration`)이 등록해 주는 것이라, 이걸 관찰하려면 18주차(자동 설정) 메커니즘을 먼저 다뤄야 한다. 지금 억지로 끌어오면 이번 주의 초점(`SpringApplication` 자체의 생명주기)이 흐려진다.

## 9. 공식 테스트 분석

`spring-boot` v3.5.0의 **릴리스 소스**(Maven Central의 `spring-boot-3.5.0-sources.jar`)로 확인했다. 이 저장소에는 `spring-framework-src`처럼 로컬에 clone해 둔 `spring-boot` 소스가 없어서, 공식 테스트 코드는 직접 열람하지 못했다(테스트는 별도 소스 jar로 배포되지 않는다) — 정직하게 밝혀 둔다. 대신 다음을 실제 릴리스 소스로 직접 확인했다.

- `SpringApplication#prepareContext`의 실제 소스: `listeners.contextPrepared(context)` 호출이 `load(context, sources)`(=`BeanDefinition` 등록) **이전**에 있고, `listeners.contextLoaded(context)` 호출은 그 **이후, `refreshContext()` 호출 이전**에 있다는 것을 코드 순서 그대로 확인했다 — 우리 실험의 `beanLookupAvailable` 결과(8번)가 왜 그렇게 나오는지의 직접적인 근거다.
- `EventPublishingRunListener`의 실제 소스: `initialMulticaster`라는 별도의 `SimpleApplicationEventMulticaster`가 있고, `refreshApplicationListeners()`가 `this.application.getListeners()`(= `addListeners()`로 등록된 것들)만 여기 등록한다는 것, 그리고 `contextLoaded()` 시점에 **비로소** 그 리스너들을 `context.addApplicationListener()`로 실제 컨텍스트에도 등록한다는 것을 확인했다 - `@Component` 리스너가 초기 이벤트를 놓치는 이유(8번)의 근거다.
- `DefaultApplicationContextFactory`의 실제 소스: `SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class, ...)`로 후보를 찾고, 전부 `null`을 반환하면 `new AnnotationConfigApplicationContext()`로 폴백한다는 것을 확인했다.

## 10. 축소 구현 (이번 주는 생략)

이번 주는 mini 구현을 만들지 않았다. `SpringApplication`의 핵심(단계별 이벤트 발행 + 두 개의 멀티캐스터)은 로직이 복잡해서가 아니라 **타이밍**이 전부인 주제라, 축소 재구현보다 실제 소스를 정확히 읽고 실행으로 확인하는 쪽이 학습 효율이 높다고 판단했다. 대신 실험(8번)에서 실제 `SpringApplication`을 두 가지 방식(리스너 직접 등록 vs 빈 등록)으로 나란히 돌려 비교했다 - 이 자체가 "구현"보다 "메커니즘 이해"에 집중하는 이번 주의 성격을 반영한다.

## 11. Spring 설계 의도

- **왜 `EventPublishingRunListener`는 컨텍스트의 멀티캐스터가 아니라 자신만의 `initialMulticaster`를 따로 두는가**: 컨텍스트 자신의 이벤트 발행 인프라(`ApplicationEventMulticaster` 빈)는 `refresh()`가 어느 정도 진행돼야(정확히는 `initApplicationEventMulticaster()` 단계, 3주차의 12단계 중 하나) 존재한다. 그런데 `ApplicationStartingEvent`처럼 컨텍스트 자체가 없는 시점부터 이벤트를 발행하려면, 컨텍스트에 의존하지 않는 별도의 발행 경로가 필요하다 - `initialMulticaster`가 그 역할이다. `contextLoaded()` 시점에 그동안 모아 둔 리스너들을 진짜 컨텍스트에도 등록해 주는 것은, 이후 이벤트(`ApplicationStartedEvent` 등)부터는 컨텍스트의 정규 이벤트 발행 경로(`context.publishEvent()`)로 자연스럽게 넘어가기 위한 "인계" 작업이다.
- **왜 `@Component` 리스너는 초기 이벤트를 못 받는 게 "버그"가 아니라 "당연한 결과"인가**: 이벤트를 받으려면 리스너 자신이 먼저 존재해야 하는데, `@Component` 리스너는 컨테이너가 만들어 주는 객체다. 컨테이너(컨텍스트)조차 없는 시점의 이벤트를, 그 컨테이너가 나중에 만들 객체가 받을 방법은 애초에 없다 - `SpringApplication.addListeners()`가 별도로 존재하는 이유는 정확히 이 시점 문제를 우회하기 위해서다: "컨테이너 밖에서, 컨테이너가 생기기 전부터 미리 등록해 둔 리스너"라는 것이다.
- **왜 `ApplicationContext` 구현체 선택이 하드코딩된 분기가 아니라 `SpringFactoriesLoader` 기반 탐색인가**: `spring-boot`(core) 모듈 자체는 웹 애플리케이션이 서블릿 기반인지 리액티브 기반인지 전혀 몰라야 한다 - 그건 `spring-boot-starter-web`/`spring-boot-webflux`처럼 별도 모듈의 관심사다. `ApplicationContextFactory`를 `SpringFactoriesLoader`로 찾게 만들면, 이 별도 모듈들이 자신의 `META-INF/spring.factories`에 "나는 이 `WebApplicationType`을 처리할 수 있다"고 스스로 알리기만 하면 되고, `spring-boot` core는 그 존재 여부조차 몰라도 된다 - 18주차에서 다룰 자동 설정(`@EnableAutoConfiguration`)과 완전히 같은 설계 철학(모듈이 스스로를 등록하게 하고, core는 등록된 것을 찾아 쓸 뿐)이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Component`로 등록한 리스너가 이벤트 8개 중 일부만 받는다는 것 - "빈으로 등록하면 당연히 다 받겠지"라는 직관이 "리스너 자신도 컨테이너가 만드는 산출물"이라는 사실 앞에서 깨졌다.
- 예상 밖이었던 것: `ApplicationContextInitializedEvent`와 `ApplicationPreparedEvent` 둘 다에서 빈 조회가 안 된다는 것 - 전자는 `BeanDefinition`조차 없어서, 후자는 `BeanDefinition`은 있어도 `refresh()`가 아직이라서, 서로 다른 이유로 같은 결과가 난다는 걸 소스로 구분해서 확인했다.
- 예상대로였던 것(재확인): 이벤트 발행 순서 자체는 레퍼런스 문서에 나열된 그대로였다.
- 새로 배운 것: `ApplicationContextFactory`의 `SpringFactoriesLoader` 기반 탐색 - 이번 16주 동안 봐 온 "확장점을 좁게 쪼개고 스스로 등록하게 한다"는 원칙(회고 문서 2번 절)이 Spring Boot에서는 "클래스패스에 있는 모듈이 자신을 스스로 알린다"는 형태로 한 단계 더 나아간다는 것을 확인했다 - 다음 주(자동 설정)의 핵심 메커니즘이 바로 이 확장이다.
- 다음 주로 이어지는 질문: `SpringFactoriesLoader`로 후보를 찾는 이 패턴이 `@EnableAutoConfiguration`에서는 어떻게 "조건부로" 걸러지는가(`@ConditionalOnClass`, `@ConditionalOnMissingBean` 등) - 18주차(자동 설정)로 이어진다.
