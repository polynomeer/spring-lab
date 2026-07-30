# 주차별 로드맵

이 문서는 [`00-methodology.md`](./00-methodology.md)의 순환 방법론을 주차별 주제에 적용한 로드맵이다. 구체적인 구현 프로젝트와 코드 스켈레톤은 [`02-project-catalog.md`](./02-project-catalog.md)에 있다.

## 학습 목표

다음 질문에 소스코드 수준으로 답할 수 있게 되는 것이 최종 목표다.

- `ApplicationContext`는 어떻게 생성되고 초기화되는가?
- `@Component`, `@Configuration`, `@Bean`은 어떻게 빈으로 등록되는가?
- `@Autowired`는 주입 대상을 어떻게 선택하는가?
- 빈은 언제 생성되고 어디에 저장되는가?
- `BeanPostProcessor`는 빈 생성 과정에 어떻게 개입하는가?
- Spring AOP 프록시는 언제 만들어지는가?
- `@Transactional`은 어떻게 트랜잭션을 시작하고 종료하는가?
- `DispatcherServlet`은 HTTP 요청을 어떻게 컨트롤러까지 전달하는가?
- Spring Boot는 Framework 위에서 무엇을 자동화하는가?

결과물: Spring 내부 호출 흐름 문서, 기능별 실험 프로젝트, Mini Spring Framework, Spring Internals 포트폴리오 저장소.

## 기간

- 총 16주 + 선택 4주 (Spring Boot 내부)
- 주당 8~10시간 (평일 1시간 × 4일, 주말 4~6시간)

## 진행 상황

| 주차 | 주제 | 상태 | 문서 |
| --- | --- | --- | --- |
| 0 | 환경 구성 | 부분 완료 — `spring-framework-src`는 clone만 함(직접 빌드는 안 함), IntelliJ 대신 `tools/jdi-tracer`로 브레이크포인트 확인 | — |
| 1 | IoC와 BeanFactory | 완료 | [`01-ioc-container/bean-factory-getbean.md`](../01-ioc-container/bean-factory-getbean.md) |
| 2 | BeanDefinition과 빈 등록 | 완료 (프로젝트 4는 미착수) | [`02-bean-definition/bean-definition-registration.md`](../02-bean-definition/bean-definition-registration.md) |
| 3 | ApplicationContext.refresh() | 완료 | [`03-context-refresh/context-refresh.md`](../03-context-refresh/context-refresh.md) |
| 4 | 빈 생성과 생명주기 | 완료 | [`04-bean-lifecycle/bean-lifecycle.md`](../04-bean-lifecycle/bean-lifecycle.md) |
| 5 | BeanFactoryPostProcessor | 완료 (프로젝트 9는 1단계까지 — 나머지 3단계는 11~12주차로 미룸) | [`05-beanfactory-postprocessor/beanfactory-postprocessor.md`](../05-beanfactory-postprocessor/beanfactory-postprocessor.md) |
| 6 | BeanPostProcessor | 완료 (새 실험 없이 4·5주차 종합 + `getEarlyBeanReference` 소스 분석) | [`06-beanpostprocessor/beanpostprocessor.md`](../06-beanpostprocessor/beanpostprocessor.md) |
| 7 | 컴포넌트 스캔 | 완료 | [`07-component-scan/component-scan.md`](../07-component-scan/component-scan.md) |
| 8 | `@Configuration`과 `@Bean` | 완료 | [`08-configuration-bean/configuration-bean.md`](../08-configuration-bean/configuration-bean.md) |
| 9 | 생성자 주입과 의존성 탐색 | 완료 | [`09-dependency-resolution/dependency-resolution.md`](../09-dependency-resolution/dependency-resolution.md) |
| 10 | `@Primary`, `@Qualifier`와 순환 참조 | 완료 | [`10-primary-qualifier-circular/primary-qualifier-circular.md`](../10-primary-qualifier-circular/primary-qualifier-circular.md) |
| 11 | 프록시와 인터셉터 체인 | 완료 | [`11-proxy-interceptor/proxy-interceptor.md`](../11-proxy-interceptor/proxy-interceptor.md) |
| 12 | 자동 프록시 생성과 self-invocation | 완료 | [`12-auto-proxy-creator/auto-proxy-creator.md`](../12-auto-proxy-creator/auto-proxy-creator.md) |
| 13 | @Transactional 내부 동작 | 완료 | [`13-transactional-internals/transactional-internals.md`](../13-transactional-internals/transactional-internals.md) |
| 14 | 트랜잭션 전파와 자원 바인딩 | 완료 | [`14-transaction-propagation/transaction-propagation.md`](../14-transaction-propagation/transaction-propagation.md) |
| 15 | DispatcherServlet 요청 처리 | 완료 | [`15-dispatcher-servlet/dispatcher-servlet.md`](../15-dispatcher-servlet/dispatcher-servlet.md) |
| 16 | 컨트롤러 메서드 호출과 응답 변환 | 완료 | [`16-controller-invocation/controller-invocation.md`](../16-controller-invocation/controller-invocation.md) |
| 17 | SpringApplication | 코드 완료, 문서 작성 전 | — |
| 18~20 | 자동 설정 이후(Spring Boot 내부) | 미착수 | — |

구체적으로 어떤 프로젝트가 끝났는지는 [`02-project-catalog.md`](./02-project-catalog.md)의 진행 상황 표를 참고한다.

## 0주차: 환경 구성

버전 고정은 [`00-methodology.md`](./00-methodology.md#버전-고정)를 따른다.

완료 조건:
- Spring Framework 저장소 빌드 성공
- IntelliJ에서 소스코드 탐색 가능
- 학습용 애플리케이션 실행 성공
- 브레이크포인트를 걸고 Framework 내부 진입 성공

---

## 1단계: IoC 컨테이너 (1~2주차)

### 1주차: IoC와 BeanFactory

**공식 문서**: IoC Container, Introduction to the Spring IoC Container, Bean Overview, Container Overview

**핵심 타입**
```text
BeanFactory
ListableBeanFactory
HierarchicalBeanFactory
AutowireCapableBeanFactory
ApplicationContext
ConfigurableApplicationContext
```

**핵심 질문**
- IoC란 무엇인가? DI와 IoC는 어떻게 다른가?
- `BeanFactory`와 `ApplicationContext`는 어떻게 다른가?
- `BeanFactory`가 제공하는 최소 계약은 무엇인가?
- `ApplicationContext`가 추가로 제공하는 기능은 무엇인가?

**실험**
```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();
context.register(AppConfig.class);
context.refresh();

OrderService orderService = context.getBean(OrderService.class);
```

**디버깅 지점**
```text
AnnotationConfigApplicationContext 생성자
AbstractApplicationContext#refresh
DefaultListableBeanFactory#getBean
AbstractBeanFactory#doGetBean
```

**산출물**: `BeanFactory` 인터페이스 계층도, `BeanFactory`·`ApplicationContext` 비교 문서, 단순 싱글턴 저장소 구현

### 2주차: BeanDefinition과 빈 등록

**공식 문서**: Bean Overview, Configuration Metadata, Bean Definition, Bean Naming

**핵심 타입**
```text
BeanDefinition
AbstractBeanDefinition
RootBeanDefinition
GenericBeanDefinition
BeanDefinitionRegistry
DefaultListableBeanFactory
```

**핵심 질문**
- 빈 정의와 빈 인스턴스는 무엇이 다른가?
- `BeanDefinition`에는 어떤 정보가 들어 있는가?
- 빈 이름은 어떻게 결정되는가?
- 빈을 등록하는 것과 생성하는 것은 왜 분리되어 있는가?

**실험**
```java
GenericBeanDefinition definition = new GenericBeanDefinition();
definition.setBeanClass(PaymentService.class);

DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
beanFactory.registerBeanDefinition("paymentService", definition);

PaymentService service = beanFactory.getBean(PaymentService.class);
```

**소스코드 추적**: `registerBeanDefinition` → `getBean` → `doGetBean` → `getMergedLocalBeanDefinition` → `createBean`

**축소 구현**: `SimpleBeanDefinition`, `BeanDefinitionRegistry`, `SimpleBeanFactory`

**완료 조건**: 다음 흐름을 설명할 수 있어야 한다 — 설정 정보 → BeanDefinition → BeanDefinitionRegistry → 빈 생성 → 싱글턴 저장

---

## 2단계: 컨테이너 초기화와 빈 생명주기 (3~4주차)

### 3주차: ApplicationContext.refresh()

**핵심 메서드**: `AbstractApplicationContext#refresh`

**주요 흐름**
```text
prepareRefresh → obtainFreshBeanFactory → prepareBeanFactory → postProcessBeanFactory
→ invokeBeanFactoryPostProcessors → registerBeanPostProcessors → initMessageSource
→ initApplicationEventMulticaster → onRefresh → registerListeners
→ finishBeanFactoryInitialization → finishRefresh
```

각 메서드에 대해 입력 상태, 실행 작업, 변경되는 컨테이너 상태, 다음 단계로 전달되는 결과를 한 줄씩 기록한다.

**실험**
- 빈 정의만 등록하고 `refresh()` 이전에 조회
- `refresh()` 이후 빈 조회
- lazy 빈과 non-lazy 빈 생성 시점 비교
- 두 번 `refresh()` 호출
- `close()` 이후 빈 조회

**산출물**: `application-context-refresh.md`, `application-context-refresh-sequence.md`

**완료 조건**: `refresh()`의 각 단계가 왜 그 순서로 실행되는지 설명할 수 있어야 한다.

### 4주차: 빈 생성 과정과 생명주기

**핵심 타입**
```text
AbstractBeanFactory
AbstractAutowireCapableBeanFactory
DefaultSingletonBeanRegistry
DisposableBeanAdapter
```

**주요 호출 흐름**
```text
getBean → doGetBean → getSingleton → createBean → doCreateBean
→ createBeanInstance → populateBean → initializeBean → addSingleton
```

**핵심 질문**
- 빈 인스턴스는 정확히 언제 생성되는가?
- 의존성 주입은 어느 단계에서 수행되는가?
- 초기화 콜백은 언제 실행되는가?
- 싱글턴은 어디에 저장되는가?
- 빈 생성 중 예외가 발생하면 캐시는 어떻게 처리되는가?

**실험**: 생성자, `@Autowired`, `@PostConstruct`, `InitializingBean#afterPropertiesSet`, `@Bean(initMethod)`, `BeanPostProcessor` before/after, `@PreDestroy`, `DisposableBean#destroy`, `@Bean(destroyMethod)`를 모두 적용한 빈으로 실행 순서를 로그로 기록한다.

**산출물**: 빈 생명주기 순서도, 생명주기 콜백 실험 코드, Mini Bean Lifecycle 구현

---

## 3단계: 컨테이너 확장 포인트 (5~6주차)

### 5주차: BeanFactoryPostProcessor

**핵심 타입**
```text
BeanFactoryPostProcessor
BeanDefinitionRegistryPostProcessor
PropertySourcesPlaceholderConfigurer
ConfigurationClassPostProcessor
```

**핵심 구분**
```text
BeanFactoryPostProcessor → BeanDefinition을 수정
BeanPostProcessor        → 생성된 빈 인스턴스를 수정
```

**실험**: 사용자 정의 애노테이션(`@ForceLazy`)으로 특정 빈의 `BeanDefinition`을 lazy로 바꾸는 `BeanFactoryPostProcessor`를 구현한다.

**핵심 질문**
- 왜 빈 인스턴스 생성 전에 실행되어야 하는가?
- `BeanDefinitionRegistryPostProcessor`는 무엇을 추가로 할 수 있는가?
- 정적 팩토리 후처리기 메서드가 권장되는 경우는 언제인가?

**산출물**: 사용자 정의 `BeanFactoryPostProcessor`, 실행 순서 실험, 정의 단계·인스턴스 단계 비교 문서

### 6주차: BeanPostProcessor

**핵심 타입**
```text
BeanPostProcessor
InstantiationAwareBeanPostProcessor
SmartInstantiationAwareBeanPostProcessor
MergedBeanDefinitionPostProcessor
```

**핵심 질문**
- `BeanPostProcessor`는 빈 생명주기의 어느 위치에서 실행되는가?
- 프록시는 어느 후처리기에서 생성되는가?
- 인스턴스 생성 전후 후처리는 어떻게 다른가?
- 하나의 빈에 여러 후처리기가 적용되면 순서는 어떻게 정해지는가?

**실험**: `LoggingBeanPostProcessor`를 구현해 빈 이름, 실제 클래스, 프록시 여부, 초기화 전후 객체 동일성, 후처리기 실행 순서를 로그로 남긴다.

**산출물**: 후처리기 체인 축소 구현, 빈 생성 파이프라인 다이어그램, `BeanFactoryPostProcessor`·`BeanPostProcessor` 비교 문서

---

## 4단계: 설정 클래스와 컴포넌트 스캔 (7~8주차)

### 7주차: 컴포넌트 스캔

**핵심 타입**
```text
ClassPathScanningCandidateComponentProvider
ClassPathBeanDefinitionScanner
TypeFilter
MetadataReader
BeanNameGenerator
AnnotatedBeanDefinitionReader
```

**핵심 질문**
- 클래스패스는 어떻게 탐색하는가? 모든 클래스를 실제로 로딩하는가?
- `@Component` 후보는 어떻게 판단하는가?
- 빈 이름은 어떻게 생성되는가?
- include filter와 exclude filter는 어떻게 적용되는가?

**실험**: 기본 `@ComponentScan`, include/exclude filter, 커스텀 stereotype annotation, 커스텀 `BeanNameGenerator`

**축소 구현 흐름**: `@Component` 탐색 → 클래스 메타데이터 읽기 → `BeanDefinition` 생성 → Registry 등록

**산출물**: Mini Component Scanner, 스캔 후보 결정 흐름도

### 8주차: @Configuration과 @Bean

**핵심 타입**
```text
ConfigurationClassPostProcessor
ConfigurationClassParser
ConfigurationClassBeanDefinitionReader
ConfigurationClassEnhancer
BeanMethodInterceptor
```

**핵심 질문**
- `@Configuration` 클래스는 누가 분석하는가?
- `@Bean` 메서드는 어떻게 `BeanDefinition`이 되는가?
- `@Configuration`과 일반 `@Component`는 어떻게 다른가?
- `proxyBeanMethods`는 어떤 의미인가?
- `@Bean` 메서드 간 호출에서 싱글턴은 어떻게 보장되는가?

**실험**: `@Configuration(proxyBeanMethods = true)`, `@Configuration(proxyBeanMethods = false)`, `@Component` 세 가지로 같은 설정을 만들고 비교한다.

**산출물**: Full Configuration·Lite Configuration 비교, CGLIB 설정 클래스 프록시 호출 흐름, `@Bean` 등록 과정 시퀀스 다이어그램

---

## 5단계: 의존성 주입 (9~10주차)

### 9주차: 생성자 주입과 의존성 탐색

**핵심 타입**
```text
AutowiredAnnotationBeanPostProcessor
ConstructorResolver
DependencyDescriptor
DefaultListableBeanFactory#resolveDependency
DefaultListableBeanFactory#doResolveDependency
```

**주요 흐름**: 주입 지점 탐색 → `DependencyDescriptor` 생성 → 후보 빈 검색 → 후보 우선순위 판정 → 의존성 변환 → 실제 주입

**핵심 질문**
- 생성자가 여러 개이면 어떤 생성자를 선택하는가?
- `@Autowired`가 없어도 단일 생성자가 주입되는 이유는 무엇인가?
- 주입 후보는 어떻게 검색하는가? 타입이 같은 빈이 여러 개이면 어떻게 처리하는가?

**실험**: 생성자 하나/여러 개, `@Autowired(required = false)`, 주입 후보 없음, 동일 타입 빈 여러 개, `Optional` 주입, 컬렉션 주입, `ObjectProvider` 주입

**산출물**: 생성자 선택 규칙 문서, 의존성 탐색 호출 흐름, Mini Constructor Injection 구현

### 10주차: @Primary, @Qualifier와 순환 참조

**핵심 타입**
```text
AutowireCandidateResolver
QualifierAnnotationAutowireCandidateResolver
DefaultListableBeanFactory#determineAutowireCandidate
DefaultSingletonBeanRegistry
```

**실험**: `@Primary`, `@Qualifier`, 필드 이름 기반 후보 선택, 제네릭 타입 후보 선택, 생성자/setter/`@Lazy` 순환 참조

**핵심 질문**
- 후보가 여러 개일 때 우선순위는 무엇인가?
- 생성자 순환 참조는 왜 해결하기 어려운가?
- 초기 참조와 완성된 빈은 어떻게 다른가?
- 프록시 객체가 포함된 순환 참조는 왜 복잡한가?

**축소 구현 방향**: 완전한 순환 참조 해결보다 "생성 중 빈 집합 관리 → 동일 빈 재진입 탐지 → 명확한 순환 참조 예외 발생"을 먼저 구현한다.

**산출물**: 의존성 후보 선택 의사결정표, 순환 참조 실험 보고서, Mini Circular Dependency Detector

---

## 6단계: Spring AOP (11~12주차)

### 11주차: 프록시와 인터셉터 체인

**핵심 타입**
```text
ProxyFactory
ProxyCreatorSupport
AdvisedSupport
AopProxy
JdkDynamicAopProxy
ObjenesisCglibAopProxy
MethodInterceptor
ReflectiveMethodInvocation
```

**핵심 질문**
- JDK 프록시와 CGLIB 프록시는 언제 선택되는가?
- Advice는 어떻게 인터셉터로 변환되는가?
- 여러 인터셉터는 어떤 순서로 실행되는가?
- 대상 메서드는 언제 실제로 호출되는가?

**주요 호출 흐름**: 프록시 메서드 호출 → 인터셉터 목록 조회 → `ReflectiveMethodInvocation` → `proceed()` → 다음 인터셉터 → 대상 객체 메서드

**실험**: JDK Dynamic Proxy, CGLIB Proxy, 인터셉터 한 개/여러 개, 예외 발생, 반환값 변경, 프록시 클래스 출력

**축소 구현**: `MiniProxyFactory`, `MethodInterceptor`, `MethodInvocation`, `InterceptorChain`

**산출물**: 프록시 선택 기준 문서, 인터셉터 체인 구현, AOP 호출 시퀀스 다이어그램

### 12주차: 자동 프록시 생성과 self-invocation

**핵심 타입**
```text
AbstractAutoProxyCreator
AbstractAdvisorAutoProxyCreator
AnnotationAwareAspectJAutoProxyCreator
BeanFactoryAdvisorRetrievalHelper
```

**핵심 질문**
- 프록시는 컨테이너의 어느 단계에서 생성되는가?
- 어떤 빈이 프록시 대상인지 어떻게 결정하는가? Advisor는 어떻게 수집되는가?
- self-invocation이 AOP를 통과하지 않는 이유는 무엇인가?

**실험**: 같은 객체 안에서 `order()`가 `@LogExecution`이 붙은 `validate()`를 호출하는 경우와, 외부에서 `proxy.validate()`를 직접 호출하는 경우를 비교한다.

**산출물**: 자동 프록시 생성 흐름, self-invocation 재현 테스트, 프록시 경계 설명 문서

---

## 7단계: 트랜잭션 (13~14주차)

### 13주차: @Transactional 내부 동작

**공식 문서**: Transaction Management, Declarative Transaction Management, Using `@Transactional`

프록시 모드에서는 프록시를 통해 들어오는 외부 호출만 트랜잭션 인터셉터의 대상이 된다. 같은 객체 내부의 self-invocation은 일반적으로 트랜잭션 어드바이스를 통과하지 않는다.

**핵심 타입**
```text
TransactionInterceptor
TransactionAspectSupport
TransactionAttributeSource
PlatformTransactionManager
AbstractPlatformTransactionManager
DataSourceTransactionManager
TransactionStatus
```

**주요 호출 흐름**: 프록시 호출 → `TransactionInterceptor#invoke` → `invokeWithinTransaction` → `TransactionAttribute` 조회 → `TransactionManager` 선택 → 트랜잭션 시작 → 대상 메서드 실행 → commit/rollback → 자원 정리

**실험**: 정상 커밋, `RuntimeException` 롤백, Checked Exception, `rollbackFor`, `readOnly`, `timeout`, self-invocation, private 메서드, 예외를 catch한 경우

**산출물**: `@Transactional` 전체 호출 흐름, 롤백 규칙 실험표, Mini Transaction Interceptor

### 14주차: 트랜잭션 전파와 자원 바인딩

**핵심 타입**
```text
TransactionSynchronizationManager
DataSourceUtils
ConnectionHolder
AbstractPlatformTransactionManager
```

**핵심 질문**
- JDBC Connection은 어떻게 현재 스레드에 연결되는가? 같은 트랜잭션에서 동일 Connection이 사용되는 이유는?
- `REQUIRED`와 `REQUIRES_NEW`는 내부적으로 무엇이 다른가? suspend와 resume은 어떤 의미인가?
- 비동기 실행에서 트랜잭션이 전달되지 않는 이유는 무엇인가?

**실험**: `REQUIRED`, `REQUIRES_NEW`, `NESTED`, `SUPPORTS`, `NOT_SUPPORTED`, 내부/외부 트랜잭션 실패, `@Async`와 `@Transactional` 조합

**산출물**: 전파 속성별 Connection 비교표, `TransactionSynchronizationManager` ThreadLocal 조사, 전파 속성 시퀀스 다이어그램

---

## 8단계: Spring MVC (15~16주차)

### 15주차: DispatcherServlet 요청 처리

**핵심 타입**
```text
HttpServlet
HttpServletBean
FrameworkServlet
DispatcherServlet
HandlerMapping
HandlerAdapter
HandlerExecutionChain
```

**주요 호출 흐름**: Servlet Container → `HttpServlet#service` → `FrameworkServlet#service` → `processRequest` → `DispatcherServlet#doService` → `doDispatch` → `HandlerMapping` → `HandlerAdapter` → Controller

**핵심 질문**
- Servlet Container와 Spring MVC의 경계는 어디인가? `DispatcherServlet`은 언제 생성되는가?
- 요청에 맞는 컨트롤러는 어떻게 찾는가?
- `HandlerMapping`과 `HandlerAdapter`를 왜 분리했는가? 인터셉터는 어느 단계에서 실행되는가?

**실험**: 일반 `@Controller`, `@RestController`, 존재하지 않는 URL, 컨트롤러 예외, `HandlerInterceptor`, 여러 `HandlerMapping` 우선순위

**산출물**: Servlet에서 Controller까지 호출 흐름, `HandlerMapping`·`HandlerAdapter` 책임 비교, Mini Dispatcher 구현

### 16주차: 컨트롤러 메서드 호출과 응답 변환

**핵심 타입**
```text
RequestMappingHandlerMapping
RequestMappingHandlerAdapter
HandlerMethod
InvocableHandlerMethod
ServletInvocableHandlerMethod
HandlerMethodArgumentResolver
HandlerMethodReturnValueHandler
HttpMessageConverter
ExceptionHandlerExceptionResolver
```

**주요 호출 흐름**: `RequestMappingHandlerAdapter` → `invokeHandlerMethod` → `InvocableHandlerMethod` → ArgumentResolver → Controller Method → ReturnValueHandler → `HttpMessageConverter` → HTTP Response

**실험**: `@RequestParam`, `@PathVariable`, `@RequestBody`, `@ModelAttribute`, 커스텀 `HandlerMethodArgumentResolver`, JSON 응답, `ResponseEntity`, `@ExceptionHandler`, `@ControllerAdvice`

**축소 구현 흐름**: `@RequestMapping` 탐색 → URL·Handler 매핑 → 메서드 인자 변환 → Reflection 호출 → 반환값 직렬화

**최종 산출물**: Spring MVC 전체 요청 처리 다이어그램, 커스텀 Argument Resolver, Mini Web MVC, 전체 학습 회고 문서

---

## 선택 과정: Spring Boot 내부 동작 (17~20주차)

16주 과정 이후 선택적으로 4주를 추가한다.

### 17주차: SpringApplication

**핵심 타입**: `SpringApplication`, `SpringApplicationRunListeners`, `ApplicationContextFactory`, `ApplicationContextInitializer`, `ApplicationListener`

**학습 질문**: `SpringApplication.run()`은 어떤 단계를 수행하는가? Environment는 언제 생성되는가? ApplicationContext 구현체는 어떻게 선택되는가? 이벤트는 어떤 순서로 발생하는가?

### 18주차: 자동 설정

**핵심 타입**: `EnableAutoConfiguration`, `AutoConfigurationImportSelector`, `ImportCandidates`, `AutoConfigurationMetadata`

**학습 질문**: 자동 설정 후보는 어디에서 읽는가? 사용자 설정이 있으면 자동 설정이 물러나는 이유는? 자동 설정 순서는 어떻게 정해지는가?

### 19주차: 조건부 설정

**핵심 타입**: `Condition`, `SpringBootCondition`, `OnClassCondition`, `OnBeanCondition`, `OnPropertyCondition`

**실험**: `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`, 커스텀 `Condition`, Condition Evaluation Report

### 20주차: Starter와 AutoConfiguration 직접 구현

결과물: `mini-observability-spring-boot-starter` — 요청 로깅, 메서드 실행시간 측정, 커스텀 설정 프로퍼티, 조건부 빈 등록, 자동 설정, Starter 모듈 분리

---

## 매주 작성할 학습 문서 템플릿

주제별 문서 템플릿은 [`00-methodology.md`](./00-methodology.md#주제별-문서-템플릿)를 따른다.

## 학습 완료 기준

**초급**: IoC와 DI를 구분할 수 있다 / `BeanFactory`와 `ApplicationContext`를 설명할 수 있다 / `BeanDefinition`의 역할을 설명할 수 있다 / 빈 생명주기 순서를 설명할 수 있다.

**중급**: `refresh()`의 주요 단계를 설명할 수 있다 / 컴포넌트 스캔과 설정 클래스 파싱 흐름을 설명할 수 있다 / `@Autowired` 후보 선택 과정을 설명할 수 있다 / `BeanPostProcessor`가 프록시 생성에 어떻게 활용되는지 설명할 수 있다.

**고급**: AOP 인터셉터 체인을 소스코드로 추적할 수 있다 / `@Transactional` 호출 흐름과 전파 속성을 설명할 수 있다 / `DispatcherServlet`부터 응답 직렬화까지 설명할 수 있다 / 장애나 예상하지 못한 동작이 발생했을 때 Spring 내부에 직접 브레이크포인트를 설정할 수 있다 / 단순 사용법이 아니라 Framework의 설계 의도를 설명할 수 있다.

## 최종 포트폴리오 결과물

핵심 16주 전체를 가로지르는 회고는 [`retrospective/retrospective.md`](../retrospective/retrospective.md)에 정리했다 - 반복된 설계 패턴, 구현하며 발견한 버그 목록, 아래 4가지 핵심 주제에 대한 답, 학습 완료 기준 자가 점검을 담았다.

**문서**: ApplicationContext refresh 분석 / Bean 생성 생명주기 분석 / 의존성 후보 선택 알고리즘 / BeanPostProcessor와 프록시 생성 / Spring AOP 인터셉터 체인 / @Transactional 내부 동작 / 트랜잭션 전파와 ThreadLocal / DispatcherServlet 요청 처리

**구현**: Mini IoC Container / Mini Dependency Injection / Mini BeanPostProcessor / Mini AOP / Mini Transaction Manager / Mini Web MVC

**실험**: 생성자 선택 실험 / 순환 참조 실험 / JDK Proxy와 CGLIB 비교 / self-invocation 실험 / 트랜잭션 전파 실험 / ArgumentResolver 실험

**발표 가능한 핵심 주제**
```text
Spring은 어떻게 확장 가능한 객체 생성 파이프라인을 만들었는가?
BeanPostProcessor는 왜 Spring 확장성의 중심인가?
@Transactional은 애노테이션 하나로 어떻게 JDBC Connection을 관리하는가?
DispatcherServlet은 다양한 컨트롤러 호출 방식을 어떻게 추상화하는가?
```

## 핵심 원칙

공식 문서를 읽는 것 자체가 목표가 되어서는 안 된다. 매주 다음 순환을 한 번 완성하는 것이 목표다.

```text
공식 문서 → 질문 → 최소 예제 → 인터페이스 → 구현체 → 디버깅 → 공식 테스트 → 축소 구현 → 설계 의도 정리
```

한 주에 여러 주제를 얕게 공부하는 것보다, 하나의 질문을 문서·런타임·소스코드로 완전히 연결하는 편이 훨씬 효과적이다. 처음 시작할 주제로는 `ApplicationContext.refresh()`와 빈 하나가 생성되는 전체 과정이 가장 좋다. 이 흐름을 이해하면 이후 컴포넌트 스캔, 의존성 주입, `BeanPostProcessor`, AOP 프록시, 트랜잭션까지 자연스럽게 연결된다.
