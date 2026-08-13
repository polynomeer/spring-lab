# SpringApplication — ApplicationContext가 생기기도 전에 이벤트부터 발행하는 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 17주차(선택 과정: Spring Boot 내부, 1주차), [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 31(SpringApplication Lifecycle Inspector)에 대응하는 분석 문서다. 핵심 16주 과정을 마친 뒤 선택적으로 이어가는 Spring Boot 내부 동작 트랙의 첫 주다.

**후기**: 8번 절은 원래 "카탈로그가 요구하는 '웹 서버 시작 여부' 관찰은 이번 주 범위에서 뺐다 - 실제 `ServletWebServerFactory` 빈은 자동 설정(`@EnableAutoConfiguration`)이 등록해 주는 것이라, 이걸 관찰하려면 18주차(자동 설정) 메커니즘을 먼저 다뤄야 한다"며 미뤄 뒀다(`docs/retrospective/retrospective.md` 7번 절 "남겨 둔 질문"의 "선택 4주에서" 마지막 항목). 18~20주차와 그 후 이어간 실험들에서 자동 설정을 이미 여러 번 다뤘으니, 이제 그 관찰을 마쳤다 - 같은 `experiments/spring-application-lifecycle` 모듈에 실제 `WebApplicationType.SERVLET` 실행을 추가해서, 내장 톰캣이 실제로 언제 요청을 받기 시작하는지, 그 시점에 이 애플리케이션의 평범한 빈들은 이미 준비돼 있는지를 직접 관찰했다. 5·6·8·11·12번 절에 그 내용을 반영했다.

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
| (후기) `WebServerStartStopLifecycle` | 실제 Spring Boot 타입 - `ServletWebServerApplicationContext#createWebServer()`가 `registerSingleton()`으로 직접 등록하는 `SmartLifecycle` 빈. `start()`가 실제로 `webServer.start()`를 호출하고 `ServletWebServerInitializedEvent`를 발행한다 |
| (후기 실험) `WebServerReadinessObserver` | `WebServerInitializedEvent`가 발행되는 바로 그 순간에 포트가 실제로 연결을 받는지, 이 애플리케이션의 평범한 빈(`MarkerBean`)이 이미 만들어져 있는지를 직접 확인 |

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
      → (9번째 단계) onRefresh() - ServletWebServerApplicationContext가 오버라이드:
          createWebServer() - WebServer "객체"만 만들고, WebServerStartStopLifecycle
          (SmartLifecycle) 빈을 registerSingleton()으로 직접 등록 - 아직 시작 안 함
      → (11번째 단계) finishBeanFactoryInitialization() - 이 애플리케이션의 모든 싱글턴
          생성(MarkerBean 포함) - 아직 웹 서버는 시작 전
      → (12번째, 마지막 단계) finishRefresh() → LifecycleProcessor가 SmartLifecycle 빈들을
          시작 → WebServerStartStopLifecycle#start() → webServer.start()
          → ServletWebServerInitializedEvent 발행(후기: 이 시점엔 이미 싱글턴이 전부
          준비돼 있다 - 바로 위 11번째 단계가 먼저 끝났으므로)
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

(후기) [`WebServerStartupTimingTest`](../../experiments/spring-application-lifecycle/src/test/java/lab/experiments/bootlifecycle/WebServerStartupTimingTest.java) (1개, 실제 `WebApplicationType.SERVLET` + 내장 톰캣):

| 실험 | 결과 |
| --- | --- |
| `ServletWebServerInitializedEvent`가 8개 이벤트 사이 어디에 끼는가 | `ApplicationPreparedEvent`와 `ApplicationStartedEvent` 사이 - `refreshContext()` 내부(정확히는 12단계 중 마지막인 `finishRefresh()`)에서 발행된다 |
| 그 이벤트가 발행되는 순간, 포트가 실제로 TCP 연결을 받는가 | 받는다 - 소켓으로 직접 연결해서 확인 |
| 그 순간, 이 애플리케이션의 평범한 `@Component` 빈(`MarkerBean`)은 이미 만들어져 있는가 | **있다** - 예상(포트가 열리는 것과 빈이 준비되는 것이 서로 다른 단계일 것)과 달랐다. `createWebServer()`(9번째 단계, `onRefresh()`)는 `WebServer` 객체만 만들고 실제 시작은 하지 않는다 - `WebServerStartStopLifecycle`이라는 `SmartLifecycle` 빈을 등록해 둘 뿐이다. 그 빈의 `start()`(실제 `webServer.start()` + 이벤트 발행이 일어나는 지점)는 `finishRefresh()`(12번째, 마지막 단계)에서 호출되는데, 이는 모든 싱글턴을 생성하는 `finishBeanFactoryInitialization()`(11번째 단계)보다 "뒤"다 - 그래서 웹 서버가 뜰 때는 이미 애플리케이션의 빈들도 전부 준비돼 있다 |
| `run()` 완료(`ApplicationReadyEvent`까지 지난) 후 실제 HTTP 요청 | 정상 처리됨(`GET /probe` → `200 ok`) |

**직접 겪은 것**: 위 세 번째 행의 결론에 도달하기 전, `spring-boot-3.5.0.jar`를 `javap -c`로 직접 디컴파일해서 `ServletWebServerApplicationContext#createWebServer()`와 `WebServerStartStopLifecycle#start()`의 바이트코드를 확인했다 - 이 저장소에는 `spring-boot`의 소스 jar가 없어서(9번 절) 공식 테스트도, 소스 코드도 직접 열람할 수 없었기 때문이다. 처음에는 "onRefresh()가 웹 서버를 직접 start()할 것"이라고 추측하고 그에 맞춰 테스트를 작성했는데, 실행해 보니 `markerBeanAvailable` 단언이 실패했다 - 추측이 틀렸다는 것을 실행이 먼저 알려주고, 바이트코드가 그 이유(`SmartLifecycle` 간접 등록)를 확인해 줬다. 소스를 읽을 수 없을 때도 "실행해서 확인한다"는 이 저장소의 방법론이 여전히 성립한다는 걸 보여준 사례다.

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
- **(후기에서 추가) 왜 웹 서버 시작이 `onRefresh()`가 아니라 `SmartLifecycle` 빈으로 미뤄지는가**: `onRefresh()`(9번째 단계) 시점에는 아직 이 애플리케이션의 싱글턴 빈들(11번째 단계에서 만들어짐)이 준비되지 않았다 - 만약 이 시점에 바로 포트를 열어 요청을 받기 시작하면, 실제 요청을 처리할 컨트롤러/서비스 빈이 아직 없는 상태로 트래픽을 받는 위험한 창이 생긴다. `WebServer` 객체 생성(`onRefresh()`)과 실제 시작(`SmartLifecycle#start()`, `finishRefresh()`)을 분리해 두면, "이 컨텍스트의 모든 빈이 준비된 뒤에야 포트를 연다"는 순서를 `refresh()`의 표준 단계 순서(11번째 뒤에 12번째)만으로 자연스럽게 보장할 수 있다 - 웹 서버 기동을 위한 별도의 특별한 순서 제어 로직이 전혀 필요 없다는 뜻이다. `Lifecycle`/`SmartLifecycle`이라는 범용 확장점 하나가, "애플리케이션이 완전히 준비된 뒤에 시작해야 하는 것"이라는 훨씬 넓은 문제를 대신 해결해 준다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Component`로 등록한 리스너가 이벤트 8개 중 일부만 받는다는 것 - "빈으로 등록하면 당연히 다 받겠지"라는 직관이 "리스너 자신도 컨테이너가 만드는 산출물"이라는 사실 앞에서 깨졌다.
- 예상 밖이었던 것: `ApplicationContextInitializedEvent`와 `ApplicationPreparedEvent` 둘 다에서 빈 조회가 안 된다는 것 - 전자는 `BeanDefinition`조차 없어서, 후자는 `BeanDefinition`은 있어도 `refresh()`가 아직이라서, 서로 다른 이유로 같은 결과가 난다는 걸 소스로 구분해서 확인했다.
- 예상대로였던 것(재확인): 이벤트 발행 순서 자체는 레퍼런스 문서에 나열된 그대로였다.
- 새로 배운 것: `ApplicationContextFactory`의 `SpringFactoriesLoader` 기반 탐색 - 이번 16주 동안 봐 온 "확장점을 좁게 쪼개고 스스로 등록하게 한다"는 원칙(회고 문서 2번 절)이 Spring Boot에서는 "클래스패스에 있는 모듈이 자신을 스스로 알린다"는 형태로 한 단계 더 나아간다는 것을 확인했다 - 다음 주(자동 설정)의 핵심 메커니즘이 바로 이 확장이다.
- 다음 주로 이어지는 질문: `SpringFactoriesLoader`로 후보를 찾는 이 패턴이 `@EnableAutoConfiguration`에서는 어떻게 "조건부로" 걸러지는가(`@ConditionalOnClass`, `@ConditionalOnMissingBean` 등) - 18주차(자동 설정)로 이어진다.
- **(후기에서 추가) "웹 서버가 언제 뜨는가"를 실제로 확인해 보니**: 처음 세운 가설("포트가 열리는 시점"과 "애플리케이션 빈이 준비되는 시점"이 서로 다른 단계일 것)은 틀렸다 - 실행해 보니 둘 다 같은 결론(둘 다 이미 준비됨)으로 수렴했다. 하지만 그 "왜"는 예상보다 흥미로웠다: `onRefresh()`가 웹 서버를 직접 시작하는 게 아니라 `SmartLifecycle` 빈 하나를 등록해 두고, 표준 `refresh()` 순서(싱글턴 생성 → `finishRefresh()`)가 그 시작 시점을 자연스럽게 뒤로 밀어 준 것이다 - "확장점을 좁게 쪼갠다"는 이 저장소의 반복된 원칙(회고 문서 2번 절)이 여기서는 "특별한 순서 제어 로직 대신 기존 확장점(`Lifecycle`)의 표준 순서에 올라탄다"는 형태로 나타났다. 소스 jar가 없어서 바이트코드를 직접 디컴파일해야 했다는 점도, "공식 문서/테스트를 못 읽으면 못 배운다"가 아니라 "실행과 바이트코드로도 충분히 확인할 수 있다"는 걸 보여준 사례다.
- **이것으로 `docs/retrospective/retrospective.md` 7번 절 "남겨 둔 질문"에 남아 있던 마지막 항목이 채워졌다.** 핵심 16주와 선택 4주 양쪽 목록 전부에 더 이상 미착수 항목이 없다.
