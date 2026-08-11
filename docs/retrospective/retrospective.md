# 20주 회고 — Spring 내부 구조를 다시 짜 보며 배운 것

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)가 요구하는 최종 산출물이다. 1주차(IoC와 BeanFactory)부터 16주차(컨트롤러 메서드 호출과 응답 변환)까지 핵심 8단계(IoC 컨테이너 → 빈 생명주기 → 확장점 → 컴포넌트 스캔/DI → AOP → 트랜잭션 → Spring MVC)를 마친 뒤, 선택 과정인 17~20주차(Spring Boot 내부 - `SpringApplication`, 자동 설정, 조건부 설정, Starter 직접 구현)까지 이어서 완주했다. 이 문서는 그 20개 문서를 가로지르는 반복된 패턴과 직접 부딪힌 버그들을 정리하는 데 집중한다. 개별 주차의 세부 내용은 각 문서를 참고한다.

**후기**: 아래 0~7번 절은 20주차를 완주한 시점 그대로 남겨 뒀다 — 그 뒤로 21~23주차(애플리케이션 이벤트, MVC 예외 처리 우선순위, 트랜잭셔널 아웃박스)를 추가로 진행하고, `tools/learning-dashboard`(jdi-tracer 기반 시각화 도구, 7개 시나리오)를 만들고, 카탈로그에 남아 있던 마지막 두 프로젝트(4번 동적 빈 등록기, 11번 Plugin Auto Discovery)까지 마치면서 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트가 전부 완료됐다. 그 경과는 [8번 절](#8-20주-이후--카탈로그-32개-프로젝트-완주와-학습-대시보드)에 정리했다 — 특히 7번 절("남겨 둔 질문")이 "여전히 미착수"라고 적어 둔 항목들은 지금은 전부 끝났다는 것만 미리 밝혀 둔다. 그리고 그 32개 다음으로, 카탈로그가 번호를 매기지 않고 별도 절(16번 절 "종합 프로젝트 추천")로 남겨 뒀던 캡스톤 `Mini Order Platform`(IoC부터 Boot까지 6개 Phase를 하나의 애플리케이션으로 통합)까지 완주했다 — [9번 절](#9-32개-다음--mini-order-platform-캡스톤-완주)에 정리했다.

## 0. 숫자로 보는 20주

- 주제 문서 20개(`docs/01-*` ~ `docs/20-*`), 그중 19개에 mermaid 다이어그램 포함(6주차는 새 실험 없이 4·5주차를 종합하는 회고 성격이라 다이어그램 없이 소스 분석만 남김)
- 코드 모듈 27개: `experiments/` 12개(실제 Spring/Boot로 검증), `mini-spring/` 7개(축소 재구현 - 16주차 이후로는 만들지 않음, 5번 절 참고), `spring-extensions/` 7개(실제 확장점 활용, 그중 3개는 `mini-observability-starter`의 core/autoconfigure/starter), `tools/` 1개(`jdi-tracer`)
- 자동화 테스트 217개, 전부 통과(`./gradlew build` 기준)
- 공식 `spring-framework` 소스(v6.2.19 태그)를 근거로 인용한 주요 클래스/메서드: `DefaultListableBeanFactory`, `AbstractAutowireCapableBeanFactory`, `ConfigurationClassEnhancer`, `AutowiredAnnotationBeanPostProcessor`, `AbstractAutoProxyCreator`, `AbstractAdvisorAutoProxyCreator`, `TransactionAspectSupport`, `AbstractPlatformTransactionManager`, `DispatcherServlet`, `InvocableHandlerMethod`, `ConfigurationClassParser$DeferredImportSelectorHandler` 등
- 17주차부터는 `spring-boot`/`spring-boot-autoconfigure`의 실제 릴리스 소스(Maven Central의 `-sources.jar`, v3.5.0)를 근거로 삼았다 - 이 저장소에 로컬로 clone해 둔 것은 `spring-framework-src`뿐이라, Spring Boot 공식 테스트 코드는 직접 열람하지 못했다는 것을 17~20주차 문서 9번 절에 매번 정직하게 남겼다

## 1. 로드맵 단계 요약 (핵심 8단계 + 선택 1단계)

| 단계 | 주차 | 핵심 발견 한 줄 | 문서 |
| --- | --- | --- | --- |
| IoC 컨테이너 | 1~2 | `getBean(Class)`는 이름 기반 조회의 wrapper가 아니라 별도 스캔 단계를 거친다 - `BeanDefinition`의 메타데이터는 등록 경로(컴포넌트 스캔 vs `@Bean` vs 수동 등록)에 따라 형태 자체가 다르다 | [1주차](../01-ioc-container/bean-factory-getbean.md), [2주차](../02-bean-definition/bean-definition-registration.md) |
| `refresh()`와 빈 생명주기 | 3~4 | `refresh()`의 12단계는 고정된 순서지만, 그 안에서 실행되는 `BeanPostProcessor`의 순서는 "우선순위 인터페이스"보다 "언제 재등록됐는가"가 더 강하게 좌우한다 | [3주차](../03-context-refresh/context-refresh.md), [4주차](../04-bean-lifecycle/bean-lifecycle.md) |
| 확장점(BFPP/BPP) | 5~6 | 같은 인터페이스, 같은 코드라도 **등록 방법**이 **실행 시점**을 바꾼다 - 그리고 조기 노출된 참조와 최종 빈의 일치는 우연이 아니라 `earlyBeanReferences` 맵 하나로 명시적으로 보장된다 | [5주차](../05-beanfactory-postprocessor/beanfactory-postprocessor.md), [6주차](../06-beanpostprocessor/beanpostprocessor.md) |
| 컴포넌트 스캔·DI | 7~10 | 여러 후보 사이의 우선순위(생성자 선택, `@Primary`/`@Qualifier`, 순환 참조 해결)는 전부 "판단 로직"보다 "탐지/우회 규칙"에 가깝다 - `@Lazy`는 순환을 해결하는 게 아니라 순환 자체를 없애 버린다 | [7주차](../07-component-scan/component-scan.md), [8주차](../08-configuration-bean/configuration-bean.md), [9주차](../09-dependency-resolution/dependency-resolution.md), [10주차](../10-primary-qualifier-circular/primary-qualifier-circular.md) |
| Spring AOP | 11~12 | 프록시 기반 AOP의 모든 한계(self-invocation, final/private 메서드, equals/hashCode 우회)는 "버그"가 아니라 "프록시가 원본과 별개의 객체"라는 한 가지 구조적 사실에서 전부 파생된다 | [11주차](../11-proxy-interceptor/proxy-interceptor.md), [12주차](../12-auto-proxy-creator/auto-proxy-creator.md) |
| 트랜잭션 | 13~14 | `@Transactional`은 AOP 인터셉터 하나일 뿐이다 - checked 예외는 기본적으로 롤백하지 않고, `REQUIRES_NEW`는 진짜 다른 커넥션을, `NESTED`는 같은 커넥션의 savepoint를 쓴다는 것이 전파 속성의 실체다 | [13주차](../13-transactional-internals/transactional-internals.md), [14주차](../14-transaction-propagation/transaction-propagation.md) |
| Spring MVC | 15~16 | `DispatcherServlet`은 겨우 몇 줄짜리 반복문(`getHandler`)이고, 복잡성은 전부 `HandlerMapping`/`HandlerAdapter`/`ArgumentResolver`/`ReturnValueHandler`라는 잘게 쪼개진 확장점들이 만들어 낸다 | [15주차](../15-dispatcher-servlet/dispatcher-servlet.md), [16주차](../16-controller-invocation/controller-invocation.md) |
| Spring Boot 내부(선택) | 17~20 | `SpringApplication`은 컨텍스트가 생기기도 전부터 별도 멀티캐스터로 이벤트를 발행하고, 자동 설정은 `.imports` 파일 탐색(`DeferredImportSelector`로 사용자 설정 파싱이 끝난 뒤 지연 처리)과 `Condition` 인터페이스 하나로 수렴하는 조건 평가로 이뤄진다 - 직접 3모듈 스타터를 조립해서 실전 적용까지 확인했다 | [17주차](../17-spring-application/spring-application.md), [18주차](../18-auto-configuration/auto-configuration.md), [19주차](../19-conditional-configuration/conditional-configuration.md), [20주차](../20-custom-starter/custom-starter.md) |

## 2. 20주에 걸쳐 반복된 설계 패턴

같은 원칙이 서로 다른 주제에서 계속 다시 나타났다는 것 자체가, 이 패턴들이 우연이 아니라 Spring 전체를 관통하는 설계 철학이라는 증거다. 17~20주차(Spring Boot 내부)를 마치고 나니, 핵심 16주에서 뽑아낸 패턴들이 새 사실이 아니라 이미 있던 결의 반복이었다는 것이 더 분명해졌다.

### "정교한 판단"보다 "예측 가능한 순서"

- 생성자가 여럿이고 `@Autowired`가 없으면 Spring은 "가장 그럴듯한" 생성자를 추측하지 않고 기본 생성자로 물러난다(9주차).
- 여러 `HandlerMapping`이 등록되면 "더 적합한" 것을 고르지 않고 **등록 순서(order)** 대로 첫 매칭을 채택한다(15주차) - `HandlerMethodArgumentResolverComposite`도 똑같이 먼저 등록된 리졸버를 채택한다(16주차, 공식 테스트 `checkArgumentResolverOrder`로 확인).
- 이 원칙의 예외처럼 보이는 것(리터럴 경로가 변수 경로를 항상 이기는 것, 15주차)도 사실은 "등록 순서"가 아니라 "패턴 자체의 구조(`PathPattern.SPECIFICITY_COMPARATOR`)"라는 또 다른 고정된 규칙일 뿐, 여전히 "실행 시점의 똑똑한 판단"은 아니다.
- 18주차의 `AutoConfigurationSorter`는 이 원칙을 자동 설정 차원으로 그대로 확장한다 - 자동 설정끼리 서로 의존관계가 없어도, `@AutoConfiguration(before/after)`라는 **선언적** 순서 힌트만으로 위상 정렬한다. `.imports` 파일에 적힌 순서(등록 순서)조차 이 선언 앞에서는 무시된다는 것을 직접 파일 순서를 뒤바꿔서 확인했다.
- 왜 반복되는가: 똑똑한 휴리스틱은 코드베이스가 커질수록 "왜 이번엔 다르게 골랐지?"라는 디버깅 비용을 만든다. Spring은 일관되게 "무엇이 우선인지 예측 가능하게 만드는 것"을 "가장 좋은 것을 자동으로 고르는 것"보다 우선시한다.

### 확장점은 좁고 합성 가능하게 쪼갠다

- `BeanFactoryPostProcessor`(정의 수정)과 `BeanPostProcessor`(인스턴스 개입)는 서로 다른 시점의 서로 다른 관심사라 분리됐다(5~6주차).
- `HandlerMapping`("무엇을 호출할지")과 `HandlerAdapter`("어떻게 호출할지")가 분리된 것도 같은 이유다(15주차).
- 16주차에서 가장 선명하게 드러난 사례: `HandlerMethodReturnValueHandler`(반환 타입 전체를 다시 정의하는 무거운 확장점)와 `ResponseBodyAdvice`(이미 있는 처리 파이프라인에 살짝 끼어드는 가벼운 확장점)가 분리돼 있다는 것을 모르고 직접 `ReturnValueHandler`를 만들면, 전역 `ResponseBodyAdvice`를 조용히 우회해 버리는 함정에 빠진다 - 실제로 겪었다.
- 19주차에서는 이 원칙이 극단까지 간다: `@ConditionalOnClass`/`@ConditionalOnProperty`/`@ConditionalOnBean`처럼 서로 완전히 달라 보이는 애노테이션들이 전부 `Condition`이라는 메서드 하나짜리 인터페이스로 수렴한다. 커스텀 `Condition`을 직접 만들어서 표준 조건들과 똑같이 `ConditionEvaluationReport`에 기록되는 것까지 확인했다 - "새로운 조건 = 새 엔진"이 아니라 "새로운 조건 = 기존 인터페이스의 구현체 하나 추가"였다.
- 왜 반복되는가: 좁은 확장점은 서로 합성(compose)할 수 있지만, 넓은 확장점은 서로 겹치거나 충돌한다. Pointcut+Advice(11주차), BeanFactoryAdvisorRetrievalHelper의 `AopUtils.canApply()`(12주차)도 전부 "판정 로직 하나 + 그걸 호출하는 배선"으로 쪼개져 있어서 재사용이 가능했다.

### 프록시는 원본과 별개의 객체라는 사실 하나가 만드는 모든 것

- self-invocation이 어떤 어드바이스(로깅, 트랜잭션, 커스텀 프록시)에도 걸리지 않는 이유(11~13주차)
- `final`/`private` 메서드가 CGLIB로도 가로챌 수 없는 이유(11주차)
- `equals`/`hashCode`가 인터셉터 체인을 건너뛰는 이유(11주차) - 프록시 자신의 정체성으로 비교해야 하기 때문
- 조기 노출된 참조가 최종 등록된 프록시와 반드시 같아야 하는 이유, 그리고 그것을 보장하기 위해 `earlyBeanReferences` 맵이 필요한 이유(6주차)
- 이 모든 것이 "프록시 구현이 미숙해서"가 아니라 "프록시가 대상 인스턴스를 감싸는 별개의 객체"라는 한 가지 구조적 전제에서 논리적으로 따라 나온다는 것을, 11주차부터 13주차까지 세 번 다른 맥락(순수 AOP → 자동 프록시 생성 → `@Transactional`)에서 반복 확인했다.

### ThreadLocal 기반 자원 바인딩

- 순환 참조 해결의 3단계 캐시(`singletonObjects`/`earlySingletonObjects`/`singletonFactories`, 1·6주차)
- 트랜잭션의 `TransactionSynchronizationManager`(스레드에 `Connection`을 바인딩, 13~14주차)
- 둘 다 "현재 진행 중인 작업의 상태를 스레드에 걸어 두고, 나중에 같은 스레드의 다른 코드가 그 상태를 다시 찾아 쓴다"는 같은 메커니즘이다. mini 구현(`mini-container`의 `beanCreationPath`, `mini-transaction`의 `holderThreadLocal`)에서 이 메커니즘을 직접 만들어 보고서야, "왜 ThreadLocal인가"(같은 스레드=같은 논리적 작업 단위)가 왜 자연스러운 선택인지 체감했다.

### SPI: 클래스패스가 스스로를 알리게 한다

16주 핵심 과정에는 없던, 17~20주차에서 새로 뚜렷해진 패턴이다.

- `ApplicationContextFactory`(17주차)는 웹 애플리케이션 타입에 맞는 `ApplicationContext`를 어떻게 고를지 하드코딩하지 않는다 - `SpringFactoriesLoader`로 클래스패스에 등록된 후보를 찾고, 아무도 응답하지 않으면 `AnnotationConfigApplicationContext`로 폴백한다.
- 자동 설정 후보 목록(18주차)도 `spring-boot` core가 "이런 자동 설정들이 있다"고 알고 있는 게 아니라, 각 모듈이 자신의 `META-INF/spring/....imports` 파일에 스스로를 등록해 두는 방식이다.
- 둘 다 "코어 모듈은 어떤 확장이 존재하는지 몰라야 한다"는 같은 설계에서 나온다 - `spring-boot`(core)는 웹이 서블릿 기반인지 리액티브 기반인지, 어떤 자동 설정들이 클래스패스에 있는지 전혀 몰라도 되고, 그 지식은 전부 각 모듈이 스스로 등록하는 파일에 있다.
- 이건 "확장점은 좁고 합성 가능하게 쪼갠다"는 위 패턴의 자연스러운 다음 단계다 - 확장점을 좁게 쪼개는 것만으로는 부족하고, 그 확장점에 "누가 참여하는지"까지 모듈 자신이 스스로 알리게 해야 코어가 각 모듈의 존재를 몰라도 되는 완전한 분리가 이뤄진다.

## 3. 구현하다가 실제로 발견한 버그들

이 목록 자체가 이 학습 방식(읽기만 하지 않고 직접 짜 보기)이 왜 효과적이었는지를 보여준다 - 전부 "이해했다고 생각했는데 실행해 보니 아니었던" 순간들이다.

| 주차 | 버그 | 원인 |
| --- | --- | --- |
| 5 | `BeanFactoryPostProcessor`를 빈으로 등록했더니 컴포넌트 스캔보다 늦게 실행됨 | 등록 **방법**이 실행 **시점**을 바꾼다는 것을 몰랐던 것 |
| 10 | `@Lazy` 순환 참조 테스트에서 `isSameAs` 실패 | 양쪽에 `@Lazy`를 붙여서 프록시가 프록시를 감싸는 상황을 만듦 |
| 12 | 두 컨텍스트가 서로의 컴포넌트 스캔에 걸려 한 곳에서 같은 빈을 두 번 프록시함 | `@Configuration`도 `@Component`라서, 같은 패키지를 스캔하는 두 설정이 서로를 주워 담음 |
| 12 | `BeanPostProcessor`용 `@Bean`을 인스턴스 메서드로 선언 | `@Configuration` 클래스 본체가 먼저 완성돼야 해서 등록 타이밍을 놓침 - `static`으로 고침 |
| 14 | rollback-only 전파를 추가하자 "connection is closed" 오류 | `MiniTransactionInterceptor`가 `commit()`을 `proceed()`의 `try` 안에 둬서, `commit()`이 던진 예외를 "실행 실패"로 오인해 이미 끝난 트랜잭션을 또 롤백하려 함 - `TransactionAspectSupport`처럼 `commit()`을 `try` 밖으로 옮겨 고침 |
| 15 | Jackson 없이 `@RestController` 테스트가 406/415로 실패, 동시에 인터셉터 순서 테스트에서 `postHandle` 누락 | 하나의 누락된 의존성(Jackson)이 서로 무관해 보이는 두 테스트를 동시에 깨뜨림 - 예외 경로가 `postHandle`을 건너뛰기 때문 |
| 16 | mini-webmvc에 경로 변수를 추가하자 `/api/users/wrapped`가 이따금 500으로 실패 | `Class#getMethods()`의 순회 순서가 보장되지 않아 `/api/users/{id}`가 먼저 등록되면 `id="wrapped"`로 해석됨 - 15주차에서 확인한 리터럴 vs 변수 경로 specificity 문제가 mini에서 그대로 재발 |
| 20 | `autoconfigure`/`starter` 모듈에서 `api(project(...))`가 "Unresolved reference: api" 빌드 오류 | 이 저장소의 루트 빌드가 모든 서브모듈에 기본 `java` 플러그인만 적용해서, `java-library`가 제공하는 `api`/`implementation` 구분 자체가 없었음 - 두 모듈에 `` `java-library` ``를 개별 적용해서 해결 |

마지막 세 항목은 특히 인상적이다 - 14주차의 버그는 "TransactionAspectSupport의 실제 구조를 다시 확인해서" 고쳤고, 16주차의 버그는 "15주차에 이미 배운 교훈"이 새 기능을 추가하자마자 다시 검증을 요구한 사례고, 20주차의 버그는 16주 내내 단일 계층 모듈만 다루다가 처음으로 진짜 멀티모듈 라이브러리를 만들면서 마주친, 이 저장소 자체의 구조적 한계였다. 반대로 17~19주차는 실행 결과가 예측과 어긋난 적은 있어도(예: `@ConditionalOnClass`가 클래스를 초기화하지 않는다는 것) 버그 자체는 하나도 없었다 - 코드를 쓰기 전에 실제 릴리스 소스를 먼저 읽고 근거를 확보하는 습관이 굳어진 뒤였기 때문이라고 본다. 매주 배운 것이 다음 주로 그냥 넘어가는 게 아니라, 코드를 확장할 때마다 다시 시험대에 오른다.

## 4. 로드맵이 제시한 핵심 주제에 대한 답

### Spring은 어떻게 확장 가능한 객체 생성 파이프라인을 만들었는가?

`BeanDefinition`(1~2주차)이라는 메타데이터 계층으로 "무엇을 어떻게 만들지"를 실제 생성과 분리하고, `refresh()`(3주차)라는 고정된 12단계 파이프라인 곳곳에 `BeanFactoryPostProcessor`/`BeanPostProcessor`(5~6주차)라는 확장점을 심어 뒀다. 각 확장점은 좁은 책임(정의 수정 vs 인스턴스 개입, 4주차의 `MergedBeanDefinitionPostProcessor` vs 일반 `BeanPostProcessor` 구분까지)만 지므로, 서로 다른 관심사(AOP 프록시, `@PostConstruct`, `@Autowired` 등)가 전부 이 하나의 파이프라인 위에서 충돌 없이 공존한다.

### `BeanPostProcessor`는 왜 Spring 확장성의 중심인가?

인스턴스를 "고치는" 정도가 아니라 **완전히 다른 객체로 통째로 교체**할 수 있기 때문이다(5주차, `MethodTimingBeanPostProcessor`가 원본 대신 프록시를 반환하는 실험). `AbstractAutoProxyCreator`(AOP 자동 프록시, 6·12주차)와 `AbstractAdvisorAutoProxyCreator`(11~12주차)가 전부 이 하나의 확장점 위에서 구현된 기능이라는 것을 확인했다 - Spring AOP는 별도의 특별한 메커니즘이 아니라 `BeanPostProcessor`의 한 가지 활용 사례일 뿐이다.

### `@Transactional`은 애노테이션 하나로 어떻게 JDBC Connection을 관리하는가?

`@Transactional`은 결국 `TransactionInterceptor`라는 `MethodInterceptor` 하나다(13주차) - 11~12주차에서 다룬 프록시/AOP 인프라 위에 그대로 얹힌 것이다. 실제 `Connection` 관리는 `TransactionSynchronizationManager`가 스레드에 바인딩한 `ConnectionHolder`(14주차)를 통해 이뤄지고, `DataSourceUtils.getConnection()`이 이 바인딩을 조회/생성하는 창구 역할을 한다. `REQUIRED`(참여)/`REQUIRES_NEW`(suspend 후 새 커넥션)/`NESTED`(같은 커넥션의 savepoint)는 전부 "이 스레드 바인딩을 어떻게 다룰 것인가"에 대한 서로 다른 정책일 뿐, 근본 메커니즘은 하나다.

### `DispatcherServlet`은 다양한 컨트롤러 호출 방식을 어떻게 추상화하는가?

`DispatcherServlet` 자신은 "핸들러가 무엇인지" 전혀 모른다(15주차) - `HandlerMapping`이 핸들러를 찾고, 그 핸들러의 "형태"를 아는 `HandlerAdapter`가 실제 호출을 담당한다. 애노테이션 기반 컨트롤러(`HandlerMethod`)의 경우 그 호출 자체도 다시 `ArgumentResolver`(인자 채우기)와 `ReturnValueHandler`+`ResponseBodyAdvice`(응답 만들기)로 잘게 쪼개져 있다(16주차). 결과적으로 `DispatcherServlet`부터 시작하는 요청 처리 전체가, 각자 좁은 책임을 지는 확장점들의 체인일 뿐 하나의 거대한 로직 덩어리가 아니라는 것이 이번 학습에서 가장 분명해진 그림이다.

### Spring Boot는 Framework 위에서 무엇을 자동화하는가?

로드맵의 학습 목표에 처음부터 있었지만 핵심 16주만으로는 답할 수 없던 질문이다. 17~20주차를 마치고 나니 답은 "새로운 프레임워크가 아니라, Spring Framework가 이미 제공하는 확장점들을 자동으로 배선해 주는 것"이었다 - `@EnableAutoConfiguration`은 Spring Framework 코어의 `@Import`(그중에서도 `DeferredImportSelector`, 18주차)일 뿐이고, `Condition`(19주차)도 Spring Framework 코어 인터페이스다. Boot가 실제로 새로 만든 것은 `.imports` 파일 기반 후보 탐색(`ImportCandidates`)과 `@ConditionalOnXxx`라는 조건 구현체들, 그리고 이 둘을 "사용자가 명시적으로 설정한 것이 없으면"이라는 규칙으로 엮는 관례(`@ConditionalOnMissingBean`)뿐이다 - 17주차에서 확인했듯 `SpringApplication.run()` 자체도 `AnnotationConfigApplicationContext` 없이는 아무것도 아니다. Boot는 Framework 위에 얹힌 아주 얇고 정교한 자동 배선 계층이다.

## 5. Mini Spring Framework 지도

각 mini 모듈이 서로 어떻게 의존하는지, 어느 주차에 만들어졌는지:

```text
mini-container (1·4·7·8·9·10주차, project 2/7/10/13/15/17)
  └─ IoC 컨테이너 핵심: BeanDefinition, BeanPostProcessor, 생성자 주입, 순환 참조 탐지
mini-component-scan (7주차, project 10) → mini-container 확장
mini-java-config (8주차, project 13) → mini-container에 의존

mini-aop (11주차, project 19)
  └─ MethodInterceptor/MethodInvocation/MiniProxyFactory - mini-container와 독립적
mini-auto-proxy (12주차, project 20) → mini-container + mini-aop에 의존
  └─ BeanPostProcessor(mini-container)와 인터셉터 체인(mini-aop)이 만나는 지점
mini-transaction (13~14주차, project 22) → mini-aop에 의존
  └─ MiniTransactionInterceptor는 mini-aop의 MethodInterceptor 하나일 뿐

mini-webmvc (15~16주차, project 27)
  └─ Servlet API 위의 독립적인 요청 처리 파이프라인 - 다른 mini 모듈에 의존하지 않음
```

이 의존 구조 자체가 4번의 답을 그대로 반영한다 - `mini-auto-proxy`가 "빈 생성 후처리"(mini-container)와 "인터셉터 체인"(mini-aop)을 잇는 지점이라는 것, `mini-transaction`이 그 인터셉터 체인 위에 정책 하나를 얹은 것뿐이라는 것.

**mini-spring 트랙은 `mini-webmvc`(16주차)에서 끝난다.** 17~19주차(`SpringApplication`, 자동 설정, 조건부 설정)는 로직의 복잡도가 아니라 **타이밍**(언제 이벤트가 발행되는지, 언제 후보가 평가되는지)이 핵심이라, 축소 재구현보다 실제 릴리스 소스를 읽고 실행으로 확인하는 쪽이 학습 효율이 높다고 각 문서 10번 절에서 판단했다. 대신 20주차의 `mini-observability-starter`는 "mini"라는 이름이 붙었지만 성격이 다르다 - 메커니즘을 축소 재구현한 것이 아니라, 17~19주차에서 배운 실제 메커니즘(`.imports` 탐색, `Condition` 평가, `ObjectProvider` 기반 안전한 조건부 배선)을 그대로 사용해 만든 **진짜 동작하는 3모듈 Spring Boot 라이브러리**다.

## 6. 학습 완료 기준 자가 점검

`docs/plan/01-roadmap.md`의 기준을 그대로 가져와 각 항목의 근거를 링크한다.

**초급**
- IoC와 DI 구분: [1주차](../01-ioc-container/bean-factory-getbean.md)
- `BeanFactory`/`ApplicationContext` 설명: [1주차](../01-ioc-container/bean-factory-getbean.md), [3주차](../03-context-refresh/context-refresh.md)
- `BeanDefinition`의 역할: [2주차](../02-bean-definition/bean-definition-registration.md)
- 빈 생명주기 순서: [4주차](../04-bean-lifecycle/bean-lifecycle.md)

**중급**
- `refresh()`의 주요 단계: [3주차](../03-context-refresh/context-refresh.md)(`tools/jdi-tracer`로 12단계 전부 실제 호출 스택 확인)
- 컴포넌트 스캔/설정 클래스 파싱 흐름: [7주차](../07-component-scan/component-scan.md), [8주차](../08-configuration-bean/configuration-bean.md)
- `@Autowired` 후보 선택 과정: [9주차](../09-dependency-resolution/dependency-resolution.md), [10주차](../10-primary-qualifier-circular/primary-qualifier-circular.md)
- `BeanPostProcessor`와 프록시 생성의 관계: [5주차](../05-beanfactory-postprocessor/beanfactory-postprocessor.md) 13번 절, [6주차](../06-beanpostprocessor/beanpostprocessor.md)

**고급**
- AOP 인터셉터 체인을 소스코드로 추적: [11주차](../11-proxy-interceptor/proxy-interceptor.md)(`ReflectiveMethodInvocation` 실제 구조까지 mini와 비교)
- `@Transactional` 호출 흐름과 전파 속성: [13주차](../13-transactional-internals/transactional-internals.md), [14주차](../14-transaction-propagation/transaction-propagation.md)
- `DispatcherServlet`부터 응답 직렬화까지: [15주차](../15-dispatcher-servlet/dispatcher-servlet.md), [16주차](../16-controller-invocation/controller-invocation.md)
- Spring 내부에 직접 브레이크포인트 설정: `tools/jdi-tracer`로 1·3주차에서 실제 사용 - 이후 주차는 실행 결과(공식 테스트 재현)와 소스 확인으로 대체하는 경우가 많아졌다(효율을 위한 의도적 선택, 각 문서 7번 절에 명시)
- 단순 사용법이 아니라 설계 의도 설명: 각 문서의 11번 절("Spring 설계 의도") 20개 전부

**선택 과정(17~20주차)**은 로드맵의 "학습 완료 기준"에 별도 항목으로 명시돼 있지는 않지만, 로드맵 최상단의 학습 목표 중 하나("Spring Boot는 Framework 위에서 무엇을 자동화하는가?")를 4번 절에서 완결지었고, `SpringApplication`/자동 설정/조건부 설정/Starter 조립까지 전부 실행 결과와 실제 릴리스 소스로 뒷받침했다.

## 7. 남겨 둔 질문

의도적으로 범위 밖에 둔 것들(각 문서 10번 절에 기록됨) 중 특히 다시 다뤄볼 만한 것:

**핵심 16주에서**
- mini 구현들의 일관된 생략: JSON 실제 역직렬화(mini-webmvc), CGLIB 상당 서브클래스 프록시(mini-aop), NESTED 전파(mini-transaction), `@ControllerAdvice` 전역 예외 처리(mini-webmvc) - 전부 "핵심 메커니즘을 이해하는 데는 필요 없었던" 것들이다.
- 7주차에서 남긴 ASM 기반 컴포넌트 스캔의 실제 성능/안전성 비교는 시도하지 않았다.
- 16주차에서 발견한 mini-webmvc의 인자 리졸버 캐싱 부재는 정확성에는 영향 없지만 실제라면 성능 이슈가 됐을 것이다.

**선택 4주에서**
- 17주차에서 미룬 "웹 서버가 실제로 언제 뜨는가"는 자동 설정(18주차) 없이는 관찰할 수 없어서 미뤘는데, 20주차에서 실제 웹 자동 설정을 통합했지만 이 관찰 자체는 별도로 다시 다루지 않았다.
- 20주차의 `mini-observability-starter`는 실제 Micrometer `ObservationRegistry` 연동 대신 인메모리 `ObservationLog`로 단순화했다 - 관찰 결과를 테스트에서 직접 조회하기 위한 의도적 선택이었지만, 실제 프로덕션 스타터라면 이 자리가 핵심이다.
- ~~카탈로그의 project 23(Transactional Outbox), 28(Error Handling Pipeline), 29~30(Application Event Bus/Mini Event Multicaster)는 여전히 미착수다~~ — 8번 절에 적었듯 넷 다 이후에 마쳤다. 이벤트 시스템과 예외 처리 파이프라인을 이번 20주 어디에서도 전용 주제로 다루지 않았다는 관찰 자체는 (그 시점 기준으로는) 정확했다.

이것으로 로드맵의 20주 전체(핵심 16주 + 선택 4주)가 마무리된다. 남은 것은 위 목록의 개별 항목들을 골라 더 깊이 파는 것뿐이다 — 그중 상당수를 실제로 이어간 기록이 8번 절에 있다.

## 8. 20주 이후 — 카탈로그 32개 프로젝트 완주와 학습 대시보드

20주차를 마친 뒤에도 세 갈래로 계속 이어갔다: 로드맵 너머의 새 주제(21~23주차), 그 주제들을 브라우저에서 직접 조작하며 보는 시각화 도구(`tools/learning-dashboard`), 그리고 7번 절이 "여전히 미착수"로 남겨 뒀던 카탈로그의 마지막 항목들. 결과적으로 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트가 전부 완료됐다 — 그 표에 더 이상 "미착수" 행이 없다.

### 8.1 숫자로 보는 지금

- 주제 문서 23개(`docs/01-*` ~ `docs/23-*`), 그중 22개에 mermaid 다이어그램 포함(여전히 6주차만 다이어그램 없음 - 0번 절에서 밝힌 이유 그대로)
- 코드 모듈 34개: `experiments/` 14개, `mini-spring/` 8개, `spring-extensions/` 8개(그중 3개는 `mini-observability-starter`), `sample-app/` 2개, `tools/` 2개(`jdi-tracer`, `learning-dashboard/backend`)
- 자동화 테스트 283개, 전부 통과(`./gradlew build` 기준) — 20주차 시점의 217개에서 66개 늘었다
- `docs/plan/02-project-catalog.md`의 32개 프로젝트 전부 완료

### 8.2 21~23주차 — 로드맵 너머로 확장한 주제

원래 20주 로드맵에는 없던 주제들이다. 순서대로 "동기 이벤트 멀티캐스트가 리스너 예외 하나로 전부 멈추는 것과 `@TransactionalEventListener`가 커밋 후에만 실행되는 것"([21주차](../21-application-events/application-events.md)), "컨트롤러 로컬 `@ExceptionHandler`가 `@ControllerAdvice`보다 항상 먼저 이기는 구조적 순서와 세 리졸버(`ExceptionHandler`→`ResponseStatus`→`Default`)의 우선순위"([22주차](../22-mvc-exception-handling/mvc-exception-handling.md)), "DB 트랜잭션이 보장하는 것(원자성)과 보장하지 않는 것(트랜잭션 밖 부수효과의 원자성) 사이의 간극을 아웃박스 패턴으로 메우는 법"([23주차](../23-transactional-outbox/transactional-outbox.md))을 확인했다.

### 8.3 학습 대시보드 — jdi-tracer를 브라우저로

[`tools/learning-dashboard`](../../tools/learning-dashboard)는 `tools/jdi-tracer`(1·3주차부터 써 온 JDI 드라이버)를 웹소켓으로 확장해서, 브레이크포인트 히트를 텍스트가 아니라 실시간 그래프/스윔레인/파이프라인으로 보여주는 React/Spring Boot 앱이다. 4주차(빈 생명주기), 12주차(자동 프록시), 14주차(트랜잭션 전파), 15~16주차(DispatcherServlet), 18~19주차(자동 설정/조건부 설정), 21~22주차(이벤트/예외 처리)를 가로지르는 7개 시나리오가 전부 실제로 동작한다 — 브라우저에서 Step/Play로 재생하거나(대부분의 시나리오), 실제 HTTP 요청을 주입하거나(15~16·22주차 시나리오), 프로퍼티를 바꿔 다시 실행하고 조건 평가 리포트 트리를 보는(18~19주차 시나리오) 세 가지 상호작용 방식으로 나뉜다.

이 도구를 만드는 과정 자체가 3번 절의 교훈("이해했다고 생각했는데 실행해 보니 아니었다")을 **이미 다 써 둔 문서에 대해서도** 다시 확인시켜 줬다 — 21·22주차 문서는 원래 소스 읽기와 `MockMvc`/유닛 테스트만으로 검증됐고 `tools/jdi-tracer`로 직접 추적하지는 않았는데, 대시보드 시나리오를 만들며 처음으로 실제 jdi-tracer 세션을 붙여 보니 둘 다 문서에 없던 걸 찾아냈다: 21주차 문서가 적어 둔 `TransactionalApplicationListenerMethodAdapter#processEventWithCallback`이라는 메서드는 실제로 존재하지 않았고(실제 지연 실행 지점은 `TransactionalApplicationListenerSynchronization#processEventWithCallbacks`, 이름도 클래스도 다르다), 22주차 문서는 `ResponseStatusExceptionResolver#doResolveException`이 원인 예외로 재귀 호출된다는 걸 언급하지 않고 있었다. 두 문서 모두 그 자리에서 고쳤다 - "테스트를 통과했다"와 "실제 실행 경로를 다 봤다"가 다르다는 걸, 이미 완료 표시가 된 문서에서도 다시 겪은 셈이다.

### 8.4 카탈로그의 마지막 두 프로젝트

7번 절이 목록에 넣지 않았던, 진짜 마지막까지 남아 있던 두 개다.

- **프로젝트 4(동적 빈 등록기)**: [`spring-extensions/dynamic-client-registry`](../../spring-extensions/dynamic-client-registry) - `BeanDefinitionRegistryPostProcessor`로 YAML 설정에 나열된 만큼 빈을 동적으로 등록한다. 5주차의 `ConfigRewriterLab`이 코드에 이미 있는 빈을 등록/제거하는 것과 달리, 이번엔 컴파일 시점엔 몇 개가 등록될지조차 모른다 - Spring Boot 자동 설정의 기초 구조에 정확히 대응한다. 이름이 중복되면 `registerBeanDefinition()`이 예외 없이 조용히 덮어쓰지만, `allowBeanDefinitionOverriding(false)`면 `BeanDefinitionOverrideException`이 다른 예외로 감싸이지 않고 그대로 전파된다는 것도 이번에 직접 확인했다.
- **프로젝트 11(Plugin Auto Discovery)**: [`sample-app/plugin-discovery-system`](../../sample-app/plugin-discovery-system) - 컴포넌트 스캔으로 찾은 `NotificationPlugin` 구현체들을 전략 패턴처럼 타입 문자열로 조회한다. Spring이 자동으로 주입해 주는 `Map<String, T>`의 키가 우리가 정의한 `type()`이 아니라 **빈 이름**이라는 걸(처음엔 `type()`일 거라 예상했다) 직접 확인하고서야, 왜 별도의 레지스트리가 필요한지 몸으로 이해했다.

### 8.5 마무리

2번 절에서 뽑아낸 패턴들("확장점은 좁고 합성 가능하게 쪼갠다", "정교한 판단보다 예측 가능한 순서")은 21~23주차와 마지막 두 프로젝트에서도 형태만 바뀌어 그대로 반복됐다 - `BeanDefinitionRegistryPostProcessor` 하나가 코드 기반이든(5주차) 설정 기반이든(프로젝트 4) 조건 기반이든(18~19주차) 같은 확장점으로 수렴하는 것처럼. 그리고 3번 절의 결론("직접 실행해서 확인하기 전엔 안다고 확신할 수 없다")은 새 주제뿐 아니라 **이미 완료 표시를 해 둔 문서**에도 예외가 아니었다(8.3절) - 이 저장소의 방법론(`docs/plan/00-methodology.md`의 순환)이 "한 번 통과하면 끝"이 아니라, 다른 도구·다른 각도로 다시 검증할 때마다 값어치를 계속 낸다는 것을 32번째 프로젝트까지 와서 다시 확인한 셈이다.

## 9. 32개 다음 — Mini Order Platform 캡스톤 완주

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 16번 절 "종합 프로젝트 추천"은 32개 번호 프로젝트와 별도로, 회원/상품/주문/결제/알림/감사 로그 도메인 위에서 IoC부터 Boot까지 배운 것 전부를 하나의 애플리케이션으로 통합하는 캡스톤(`sample-app/mini-order-platform`)을 20주 로드맵의 원래 마지막 단계로 그려 뒀다 - 8번 절이 마무리될 때까지도 손대지 않았던 유일한 항목이다. 여섯 개 Phase(IoC+생명주기 → AOP → 트랜잭션 → MVC → 이벤트 → Boot)로 나눠 순서대로 진행했고, 각 Phase가 끝날 때마다 실제 테스트로 검증한 뒤 다음 Phase로 넘어갔다 - 상세 설계와 발견 사항은 전부 [`docs/24-mini-order-platform/mini-order-platform.md`](../24-mini-order-platform/mini-order-platform.md)에 Phase별로 누적 기록해 뒀고, 이 절은 그중 반복해서 배울 만한 것만 추린다.

### 9.1 숫자로 보는 지금

- 코드 모듈 35개(8번 절 시점의 34개에서 `sample-app/mini-order-platform` 1개 추가) - 새 모듈이지만 새 최상위 역할(experiments/mini-spring/spring-extensions/sample-app/tools)을 만들지는 않았다, `sample-app/`의 세 번째 구성원일 뿐이다
- 이 모듈 하나의 테스트가 43개(전부 실제 Spring 컨테이너/임베디드 H2/MockMvc를 통과하는 통합 테스트, 목 없음) - 저장소 전체 자동화 테스트는 8번 절의 283개에서 326개로 늘었다
- 새 주제 문서를 쓰지 않고 `docs/24-mini-order-platform/`라는 새 디렉터리 하나로 여섯 Phase를 전부 담았다 - 다른 주제 문서들과 달리 다이어그램은 아직 없다(진행 중 누적 기록이라는 성격상 뒤로 미뤄 둔 것 - 완료된 지금은 남은 선택적 보강 항목이다)

### 9.2 여섯 Phase, 한 줄씩

- **Phase 1(IoC+생명주기)**: 같은 "여러 빈 중 선택" 문제를 세 가지 다른 방식(List→Map 재구성, `@Qualifier` 명시 고정, List 그대로 브로드캐스트)으로 나란히 구현해서 언제 뭘 쓰는지 비교했다. `PaymentGatewayClient`를 컨테이너 없이 `new`로 만들면 `@PostConstruct`가 호출되지 않는다는 걸로 "DI 컨테이너는 단순 생성이 아니다"를 재확인했다.
- **Phase 2(AOP)**: 실행시간측정/권한검사/감사로그/재시도/멱등성 다섯 개 어드바이스를 `@Order`로 명시적으로 쌓았다. 재시도가 가장 안쪽, 타이밍이 가장 바깥쪽이어야 하는 이유를 "재시도까지 포함한 전체 시간을 재야 한다"는 요구에서 거꾸로 도출했다.
- **Phase 3(트랜잭션)**: 주문+Outbox는 같은 트랜잭션으로 묶고, 결제 시도 이력은 `REQUIRES_NEW`로 분리해서 결제가 실패해도 "시도했다는 사실"만은 살아남게 했다. `OrderCancellationService`로 rollback-only 실험(내부 REQUIRED 예외를 삼켜도 트랜잭션은 이미 rollback-only)을 Order 도메인 위에서 재현했다.
- **Phase 4(MVC)**: `@RequestParam`에 커스텀 `Converter<String, PaymentMethod>`를 걸어 특정 enum 값을 거절하려다가, Spring의 `TypeConverterDelegate`가 컨버터 실패를 삼키고 `Enum#valueOf()`로 조용히 재시도하는 하위 호환 fallback을 실제로 만났다 - enum을 `@RequestParam` 타입으로 직접 쓰면 아무리 구체적인 Converter를 등록해도 그 값을 거절할 수 없다는 게 이번에 확인한 일반화되는 결론이다. 대상 타입을 enum이 아닌 래퍼 타입으로 바꿔 우회했다.
- **Phase 5(이벤트)**: "저장"(Outbox 행 기록)은 트랜잭션 안에서 직접, "부작용"(알림 발송·실제 발행)은 `@TransactionalEventListener(AFTER_COMMIT)`으로 분리했다. `TransactionTemplate`로 직접 트랜잭션을 열고 롤백시켜 "커밋되지 않으면 AFTER_COMMIT 리스너가 아예 실행되지 않는다"를 재확인했다.
- **Phase 6(Boot)**: 아래 9.3에서 따로 다룰 만큼 이 캡스톤에서 가장 오래 걸린 Phase였다.

### 9.3 가장 크게 배운 것 — 개별 Phase에서는 안 보이던 것, 합치는 순간 드러난 것

Phase 1~5는 각자 새 개념 하나씩을 새 코드로 검증하는 식이라, 지금까지의 32개 프로젝트와 본질적으로 같은 리듬이었다. Phase 6에서 처음으로 다른 종류의 버그를 만났다 - **개별 Phase 각각은 옳았는데, 여섯 개를 한 애플리케이션으로 합치는 순간에만 드러나는 상호작용**이었다.

`OrderPlatformApplication`(진입점, `@EnableAutoConfiguration`)을 다른 자동 설정 클래스들과 같은 `boot` 패키지에 뒀는데, Phase 1~5 내내 잘 써 온 `OrderPlatformConfig`/`OrderWebConfig`의 컴포넌트 스캔이 그 패키지까지 통째로 훑고 있었다. `@EnableAutoConfiguration`은 **컴포넌트 스캔으로 발견되기만 해도 그대로 활성화**된다는 걸 몰랐던 게 아니라, "이 진입점 클래스가 다른 목적의 스캔에 우연히 걸릴 수 있다"는 걸 설계 시점에 생각하지 못했다 - 그 결과 Boot 표준 자동 설정(SQL 초기화 포함) 전체가 순수 비-Boot 테스트 컨텍스트 안으로 끌려들어와 `JdbcConfig`가 이미 실행해 둔 `schema.sql`을 또 실행하려다 깨졌다. 고치고 나니 두 번째, 세 번째 문제가 연달아 나왔다 - project 32의 테스트 패턴(중첩 `@Configuration` 픽스처)이 같은 이유로 다른 테스트를 오염시켰고(`ApplicationContextRunner#withBean`으로 우회), 진짜 Boot 부트스트랩에서는 우리 스키마 초기화와 Boot의 자동 스키마 초기화가 실제로(우연이 아니라) 겹치는 진짜 충돌도 있었다(`SqlInitializationAutoConfiguration` 명시적 제외로 해결). 전부 [`docs/24-mini-order-platform/mini-order-platform.md`](../24-mini-order-platform/mini-order-platform.md) 9번 절에 순서대로 기록했다.

### 9.4 마무리

2번 절이 뽑아낸 패턴("확장점은 좁고 합성 가능하게 쪼갠다")은 이 캡스톤 안에서도 그대로였다 - AOP 다섯 어드바이스, 트랜잭션 전파 두 가지(REQUIRED/REQUIRES_NEW), 이벤트 리스너 두 개가 전부 서로 독립적으로 짜여서 필요할 때만 조합됐다. 하지만 8번 절까지는 "합성 가능한 좁은 조각들"이 실제로 **합성되는 지점**을 검증할 기회가 별로 없었다 - 매 프로젝트가 대체로 독립된 모듈이었기 때문이다. Mini Order Platform은 처음으로 그 조각들을 전부 한 애플리케이션에 실제로 합성해 봤고, 9.3의 버그들은 정확히 "각 조각은 옳지만 합쳐지는 방식이 틀렸다"는 새로운 종류의 실패였다 - 개별 개념 검증(단위 테스트 수준)과 애플리케이션 구성 검증(패키지 구조·스캔 경계·초기화 순서 수준)이 서로 다른 종류의 실수를 낳는다는 걸, 32개 프로젝트를 다 끝낸 뒤에야 이 캡스톤에서 처음 겪었다.
