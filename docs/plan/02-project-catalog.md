# 구현 프로젝트 카탈로그

이 문서는 [`01-roadmap.md`](./01-roadmap.md)의 각 주차 주제를 실제로 구현하는 32개 프로젝트의 목록과 코드 스켈레톤이다. 방법론은 [`00-methodology.md`](./00-methodology.md)를 따른다.

각 파트는 세 단계로 구성한다.

```text
설명용 예제
→ Spring이 정상적으로 동작하는 최소 사례

동작 검증 실험
→ 조건을 바꿔 Spring의 선택 규칙과 경계 조건 확인

축소 구현
→ 핵심 추상화만 직접 구현하여 설계 의도 이해
```

모든 기능을 하나의 거대한 프로젝트에 넣지 않고, 저장소 루트를 다음처럼 구성한다.

```text
spring-internals-lab/
├── experiments/       # 실제 Spring으로 동작을 검증하는 코드
├── mini-spring/       # Spring 기능을 축소 구현하는 코드
├── spring-extensions/ # Spring 확장 포인트를 사용하는 코드
├── sample-app/        # 학습한 기능을 종합 적용하는 애플리케이션
└── tools/             # jdi-tracer 등 학습용 도구
```

## 진행 상황

| # | 프로젝트 | 상태 | 코드 |
| --- | --- | --- | --- |
| 1 | 수동 IoC 컨테이너 실험 | 완료 | [`experiments/ioc-container-lab`](../../experiments/ioc-container-lab) |
| 2 | Mini BeanFactory | 완료 — 1~3단계 + 타입 기반 조회 + 순환 참조 탐지까지 확장 | [`mini-spring/mini-container`](../../mini-spring/mini-container) |
| 3 | BeanDefinition Registry Inspector | 완료 | [`experiments/bean-definition-inspector`](../../experiments/bean-definition-inspector) |
| 4 | 동적 빈 등록기 | 완료 | [`spring-extensions/dynamic-client-registry`](../../spring-extensions/dynamic-client-registry) |
| 5 | Context Refresh Visualizer | 완료 | [`experiments/context-refresh-visualizer`](../../experiments/context-refresh-visualizer) |
| 6 | Bean Lifecycle Recorder | 완료 | [`experiments/bean-lifecycle-recorder`](../../experiments/bean-lifecycle-recorder) |
| 7 | Mini Bean Lifecycle Pipeline | 완료 — 별도 모듈 대신 project 2의 `mini-container`를 확장 | [`mini-spring/mini-container`](../../mini-spring/mini-container) |
| 8 | Configuration Property Rewriter | 완료 | [`spring-extensions/configuration-property-rewriter`](../../spring-extensions/configuration-property-rewriter) |
| 9 | Method Timing BeanPostProcessor | 완료 — 2~4단계(ProxyFactory/Pointcut+Advisor/자동 프록시 생성기 비교)까지 마무리 | [`spring-extensions/method-timing-post-processor`](../../spring-extensions/method-timing-post-processor) |
| 10 | Mini Component Scanner | 완료 | [`mini-spring/mini-component-scan`](../../mini-spring/mini-component-scan) |
| 11 | Plugin Auto Discovery | 완료 | [`sample-app/plugin-discovery-system`](../../sample-app/plugin-discovery-system) |
| 12 | Configuration Proxy Experiment | 완료 | [`experiments/configuration-proxy-lab`](../../experiments/configuration-proxy-lab) |
| 13 | Mini Java Config Parser | 완료 | [`mini-spring/mini-java-config`](../../mini-spring/mini-java-config) |
| 14 | Dependency Resolution Matrix | 완료 | [`experiments/dependency-resolution-matrix`](../../experiments/dependency-resolution-matrix) |
| 15 | Mini Constructor Injector | 완료 — Optional/List/ObjectProvider 형태(4단계)는 미룸 | [`mini-spring/mini-container`](../../mini-spring/mini-container) |
| 16 | Circular Dependency Laboratory | 완료 | [`experiments/circular-dependency-lab`](../../experiments/circular-dependency-lab) |
| 17 | Mini Cycle Detector | 완료 (4주차에서 선반영) | [`mini-spring/mini-container`](../../mini-spring/mini-container) |
| 18 | Proxy Playground | 완료 | [`experiments/proxy-playground`](../../experiments/proxy-playground) |
| 19 | Mini AOP Framework | 완료 — JDK Dynamic Proxy 기반만 구현, CGLIB 상당 서브클래스 프록시는 범위 밖 | [`mini-spring/mini-aop`](../../mini-spring/mini-aop) |
| 20 | Annotation-Based Auto Proxy Creator | 완료 | [`mini-spring/mini-auto-proxy`](../../mini-spring/mini-auto-proxy) |
| 21 | Transaction Propagation Playground | 완료 | [`experiments/transaction-propagation-playground`](../../experiments/transaction-propagation-playground) |
| 22 | Mini Transaction Manager | 완료 (1~6단계) | [`mini-spring/mini-transaction`](../../mini-spring/mini-transaction) |
| 23 | Transactional Outbox Sample | 완료 | [`sample-app/transactional-outbox-order`](../../sample-app/transactional-outbox-order) |
| 24 | DispatcherServlet Trace Application | 완료 | [`experiments/dispatcher-servlet-trace`](../../experiments/dispatcher-servlet-trace) |
| 25 | Custom Argument Resolver | 완료 | [`spring-extensions/current-user-argument-resolver`](../../spring-extensions/current-user-argument-resolver) |
| 26 | Custom Return Value Handler | 완료 | [`spring-extensions/api-response-handler`](../../spring-extensions/api-response-handler) |
| 27 | Mini Web MVC | 완료 (1~6단계) | [`mini-spring/mini-webmvc`](../../mini-spring/mini-webmvc) |
| 28 | Error Handling Pipeline | 완료 | [`experiments/mvc-exception-pipeline`](../../experiments/mvc-exception-pipeline) |
| 29 | Application Event Bus | 완료 | [`experiments/application-event-lab`](../../experiments/application-event-lab) |
| 30 | Mini Event Multicaster | 완료 | [`mini-spring/mini-event`](../../mini-spring/mini-event) |
| 31 | SpringApplication Lifecycle Inspector | 기본 이벤트 순서/가용성만 완료 — 웹 서버 시작 시점 관찰은 자동 설정(18주차) 이후로 미룸 | [`experiments/spring-application-lifecycle`](../../experiments/spring-application-lifecycle) |
| 32 | Custom AutoConfiguration | 완료 (`request-observation` 대신 `mini-observability-starter`로 명명) | [`spring-extensions/mini-observability-starter`](../../spring-extensions/mini-observability-starter) |

각 프로젝트의 분석 과정과 발견한 내용은 대응하는 `docs/<NN>-<topic>/` 문서에 있다 — [`01-roadmap.md`](./01-roadmap.md)의 진행 상황 표에서 링크를 따라간다.

------

# 1. IoC와 BeanFactory

## 프로젝트 1: 수동 IoC 컨테이너 실험

### 목표

Spring Boot 없이 `BeanFactory`와 `ApplicationContext`를 직접 생성하여 차이를 확인한다.

### 구현 대상

```text
experiments/ioc-container-lab
```

### 예제 코드

```java
public class BeanFactoryLab {

    public static void main(String[] args) {
        DefaultListableBeanFactory beanFactory =
                new DefaultListableBeanFactory();

        RootBeanDefinition paymentDefinition =
                new RootBeanDefinition(PaymentService.class);

        beanFactory.registerBeanDefinition(
                "paymentService",
                paymentDefinition
        );

        PaymentService paymentService =
                beanFactory.getBean(PaymentService.class);

        paymentService.pay();
    }
}
```

### 실험 항목

- 빈 등록 전후 `containsBean()` 결과
- `getBean()`을 두 번 호출했을 때 동일 객체인지 확인
- singleton과 prototype 비교
- 이름 기반 조회와 타입 기반 조회 비교
- 동일 타입 빈이 두 개일 때 예외 확인
- 없는 빈 조회 시 예외 확인

### 확인해야 할 내부 객체

```text
DefaultListableBeanFactory
BeanDefinition
singletonObjects
beanDefinitionMap
```

------

## 프로젝트 2: Mini BeanFactory

### 목표

객체 저장소 수준에서 시작하여 BeanDefinition 기반 컨테이너로 발전시킨다.

```text
mini-spring/mini-container
```

### 1단계: 인스턴스 저장 방식

```java
public final class SimpleBeanFactory {

    private final Map<String, Object> singletonObjects =
            new ConcurrentHashMap<>();

    public void registerSingleton(String name, Object bean) {
        singletonObjects.put(name, bean);
    }

    public Object getBean(String name) {
        Object bean = singletonObjects.get(name);

        if (bean == null) {
            throw new NoSuchBeanException(name);
        }

        return bean;
    }
}
```

### 2단계: BeanDefinition 도입

```java
public record BeanDefinition(
        Class<?> beanClass,
        Scope scope
) {
}
public enum Scope {
    SINGLETON,
    PROTOTYPE
}
```

### 3단계: 지연 생성

```text
BeanDefinition 등록
→ 최초 getBean()
→ Reflection으로 객체 생성
→ singletonObjects 저장
```

### 구현 완료 조건

- Singleton과 Prototype 지원
- 이름 및 타입 조회 지원
- 중복 빈 이름 검증
- 존재하지 않는 빈 예외
- 타입이 같은 빈이 여러 개일 경우 예외
- 생성 중인 빈 상태 관리

------

# 2. BeanDefinition과 빈 등록

## 프로젝트 3: BeanDefinition Registry Inspector

### 목표

Spring 컨테이너에 어떤 `BeanDefinition`들이 등록되는지 출력한다.

```text
experiments/bean-definition-inspector
```

### 구현 대상

```java
public class BeanDefinitionInspector {

    public static void print(
            ConfigurableApplicationContext context
    ) {
        ConfigurableListableBeanFactory beanFactory =
                context.getBeanFactory();

        for (String beanName :
                beanFactory.getBeanDefinitionNames()) {

            BeanDefinition definition =
                    beanFactory.getBeanDefinition(beanName);

            System.out.printf(
                    """
                    beanName=%s
                    beanClass=%s
                    scope=%s
                    lazy=%s
                    factoryBean=%s
                    factoryMethod=%s
                    role=%d
                    %n
                    """,
                    beanName,
                    definition.getBeanClassName(),
                    definition.getScope(),
                    definition.isLazyInit(),
                    definition.getFactoryBeanName(),
                    definition.getFactoryMethodName(),
                    definition.getRole()
            );
        }
    }
}
```

### 비교 대상

- `@Component`로 등록한 빈
- `@Bean`으로 등록한 빈
- `registerBeanDefinition()`으로 등록한 빈
- Spring 내부 인프라 빈
- Factory Method 기반 빈
- Supplier 기반 빈

### 학습 포인트

다음 세 가지를 비교한다.

```text
Component Scan
→ 스캔 결과로 BeanDefinition 생성

@Bean
→ ConfigurationClassBeanDefinitionReader가 등록

registerBeanDefinition
→ 사용자가 직접 등록
```

------

## 프로젝트 4: 동적 빈 등록기

### 목표

설정 파일에 따라 여러 구현체를 동적으로 빈으로 등록한다.

```text
spring-extensions/dynamic-client-registry
```

### 예제 시나리오

```yaml
external-clients:
  - name: paymentClient
    base-url: https://payment.example.com
    timeout: 3000

  - name: orderClient
    base-url: https://order.example.com
    timeout: 5000
```

이를 읽어 다음 빈을 동적으로 등록한다.

```text
paymentClient → ExternalApiClient
orderClient   → ExternalApiClient
```

### 구현 대상

```java
public class ExternalClientRegistrar
        implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(
            BeanDefinitionRegistry registry
    ) {
        // 설정을 읽고 BeanDefinition을 동적으로 생성한다.
    }

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory
    ) {
    }
}
```

### 이해할 수 있는 개념

- `BeanDefinitionRegistry`
- 동적 빈 등록
- 인프라 빈과 애플리케이션 빈
- `BeanDefinitionRegistryPostProcessor`
- Spring Boot AutoConfiguration의 기초 구조

------

# 3. ApplicationContext.refresh()

## 프로젝트 5: Context Refresh Visualizer

### 목표

컨테이너 초기화 과정을 이벤트와 후처리기 로그로 시각화한다.

```text
experiments/context-refresh-visualizer
```

### 구현 대상

다음 단계를 로그로 남긴다.

```text
ApplicationStarting
ApplicationEnvironmentPrepared
ApplicationContextInitialized
ApplicationPrepared
BeanFactoryPostProcessor 실행
BeanPostProcessor 등록
Singleton 생성
ContextRefreshedEvent
ApplicationStarted
ApplicationReady
```

Framework만 사용할 경우에는 다음을 관찰한다.

```text
refresh() 호출 전
BeanDefinition 등록
BeanFactoryPostProcessor 실행
BeanPostProcessor 등록
non-lazy singleton 생성
ContextRefreshedEvent 발생
```

### 실험 대상

- `refresh()` 전 빈 조회
- `refresh()` 후 빈 조회
- lazy singleton
- non-lazy singleton
- prototype
- 컨텍스트 종료
- 초기화 중 예외 발생
- 부모·자식 컨텍스트

### 부모·자식 컨텍스트 예제

```java
AnnotationConfigApplicationContext parent =
        new AnnotationConfigApplicationContext(ParentConfig.class);

AnnotationConfigApplicationContext child =
        new AnnotationConfigApplicationContext();

child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
```

### 확인 항목

- 자식이 부모 빈을 조회할 수 있는지
- 부모가 자식 빈을 조회할 수 있는지
- 동일 이름 빈이 있을 때 어떤 빈이 선택되는지
- 이벤트가 어느 컨텍스트까지 전달되는지

------

# 4. 빈 생성과 생명주기

## 프로젝트 6: Bean Lifecycle Recorder

### 목표

빈 생성부터 소멸까지 모든 콜백 순서를 기록한다.

```text
experiments/bean-lifecycle-recorder
```

### 적용할 기능

```java
@Component
public class LifecycleTarget
        implements BeanNameAware,
                   BeanFactoryAware,
                   ApplicationContextAware,
                   InitializingBean,
                   DisposableBean {

    public LifecycleTarget() {
        record("constructor");
    }

    @Autowired
    public void inject(Dependency dependency) {
        record("@Autowired");
    }

    @PostConstruct
    public void postConstruct() {
        record("@PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        record("afterPropertiesSet");
    }

    @PreDestroy
    public void preDestroy() {
        record("@PreDestroy");
    }

    @Override
    public void destroy() {
        record("destroy");
    }
}
```

### 추가 대상

- `BeanPostProcessor`
- `InstantiationAwareBeanPostProcessor`
- `SmartInitializingSingleton`
- `ApplicationRunner`
- `CommandLineRunner`
- `ContextRefreshedEvent`
- custom init method
- custom destroy method

### 기대 결과

다음과 같은 순서를 직접 확인한다.

```text
BeanDefinition 등록
→ 생성자 호출
→ 의존성 주입
→ Aware 콜백
→ BeanPostProcessor before initialization
→ @PostConstruct
→ InitializingBean
→ custom init method
→ BeanPostProcessor after initialization
→ 사용
→ @PreDestroy
→ DisposableBean
→ custom destroy method
```

------

## 프로젝트 7: Mini Bean Lifecycle Pipeline

### 목표

빈 생성 파이프라인을 템플릿 메서드 구조로 직접 구현한다.

```text
mini-spring/mini-bean-lifecycle
```

### 구현할 메서드

```java
public Object createBean(String beanName) {
    BeanDefinition definition =
            beanDefinitionRegistry.get(beanName);

    Object bean = instantiateBean(definition);

    populateBean(beanName, bean, definition);

    bean = initializeBean(beanName, bean);

    registerSingleton(beanName, bean);

    return bean;
}
```

### 확장 기능

```java
public interface BeanPostProcessor {

    default Object postProcessBeforeInitialization(
            Object bean,
            String beanName
    ) {
        return bean;
    }

    default Object postProcessAfterInitialization(
            Object bean,
            String beanName
    ) {
        return bean;
    }
}
```

### 완료 조건

- 객체 생성
- 의존성 주입
- 초기화 콜백
- 후처리기 체인
- 싱글턴 저장
- 생성 실패 시 캐시 정리
- 종료 시 destroy callback 실행

------

# 5. BeanFactoryPostProcessor와 BeanPostProcessor

## 프로젝트 8: Configuration Property Rewriter

### 목표

빈이 생성되기 전에 `BeanDefinition`을 변경한다.

```text
spring-extensions/configuration-property-rewriter
```

### 예제 애노테이션

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ForcePrototype {
}
```

### 동작

`@ForcePrototype`이 붙은 클래스의 `BeanDefinition` scope를 prototype으로 변경한다.

```java
public class ForcePrototypePostProcessor
        implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory
    ) {
        for (String name :
                beanFactory.getBeanDefinitionNames()) {

            BeanDefinition definition =
                    beanFactory.getBeanDefinition(name);

            // AnnotationMetadata를 확인하여 scope를 변경한다.
        }
    }
}
```

### 실험 대상

- scope 변경
- lazy 여부 변경
- property value 추가
- primary 변경
- 특정 빈 등록 제거
- BeanDefinition 역할 변경

------

## 프로젝트 9: Method Timing BeanPostProcessor

### 목표

특정 애노테이션이 붙은 빈을 프록시로 교체한다.

```text
spring-extensions/method-timing-post-processor
```

### 애노테이션

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasureTime {
}
```

### 구현 구조

```java
public class MethodTimingBeanPostProcessor
        implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName
    ) {
        // @MeasureTime 메서드가 있으면 프록시를 생성한다.
        return bean;
    }
}
```

### 구현 방법

처음에는 JDK Dynamic Proxy로 구현하고, 그다음 `ProxyFactory`로 교체한다.

```text
1단계: java.lang.reflect.Proxy
2단계: Spring ProxyFactory
3단계: Pointcut + Advisor
4단계: 자동 프록시 생성기와 비교
```

### 학습 포인트

- 후처리기는 객체 자체를 교체할 수 있음
- 컨테이너에 저장되는 객체는 원본이 아니라 프록시일 수 있음
- `postProcessAfterInitialization()`과 프록시 생성의 관계
- AOP가 IoC 컨테이너 위에서 동작하는 이유

------

# 6. 컴포넌트 스캔

## 프로젝트 10: Mini Component Scanner

### 목표

특정 패키지의 클래스를 탐색하여 `@Component`가 붙은 클래스를 등록한다.

```text
mini-spring/mini-component-scan
```

### 구현 대상

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniComponent {

    String value() default "";
}
public final class ComponentScanner {

    public Set<Class<?>> scan(String basePackage) {
        // 클래스패스 파일을 검색한다.
        // 클래스를 로딩한다.
        // @MiniComponent 여부를 검사한다.
        // 후보를 반환한다.
    }
}
```

### 추가 구현

- 기본 빈 이름 생성
- 사용자 지정 빈 이름
- include filter
- exclude filter
- 인터페이스·추상 클래스 제외
- 중복 빈 이름 검사
- 여러 패키지 스캔

### 심화 과제

리플렉션으로 클래스를 직접 로딩하는 방식과 메타데이터만 읽는 방식을 비교한다.

```text
Reflection 기반
→ 클래스 초기화 위험 존재

MetadataReader 방식
→ 클래스 로딩 없이 메타데이터 분석 가능
```

Spring이 ASM 기반 메타데이터 리딩을 사용하는 이유를 이해하는 데 유용하다.

------

## 프로젝트 11: Plugin Auto Discovery

### 목표

특정 인터페이스 구현체를 자동으로 발견하고 등록하는 플러그인 시스템을 만든다.

```text
sample-app/plugin-discovery-system
```

### 예제 인터페이스

```java
public interface NotificationPlugin {

    String type();

    void send(NotificationMessage message);
}
```

### 구현체

```text
EmailNotificationPlugin
SlackNotificationPlugin
SmsNotificationPlugin
```

### 요구사항

- 패키지 스캔으로 플러그인 발견
- 타입별 Map 구성
- 중복 타입 검증
- 활성화 여부 설정
- 플러그인 우선순위
- 런타임 조회

```java
Map<String, NotificationPlugin> pluginMap;
```

### 연결되는 Spring 개념

- Component Scan
- 빈 이름
- 컬렉션 주입
- `@Order`
- 전략 패턴
- 플러그인 아키텍처

------

# 7. @Configuration과 @Bean

## 프로젝트 12: Configuration Proxy Experiment

### 목표

`@Configuration(proxyBeanMethods = true/false)`의 차이를 객체 동일성으로 확인한다.

```text
experiments/configuration-proxy-lab
```

### 예제

```java
@Configuration(proxyBeanMethods = true)
public class FullConfiguration {

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentService());
    }

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }
}
```

다음과 비교한다.

```java
@Configuration(proxyBeanMethods = false)
public class LiteConfiguration {
}
@Component
public class ComponentConfiguration {
}
```

### 검증 항목

```java
PaymentService contextBean =
        context.getBean(PaymentService.class);

OrderService orderService =
        context.getBean(OrderService.class);

assertSame(
        contextBean,
        orderService.getPaymentService()
);
```

### 추가 실험

- static `@Bean`
- `@Bean` 메서드 직접 호출
- 생성자 파라미터 주입
- `@Import`
- `ImportSelector`
- `ImportBeanDefinitionRegistrar`
- 순환하는 `@Bean` 메서드

------

## 프로젝트 13: Mini Java Config Parser

### 목표

직접 만든 `@MiniConfiguration`, `@MiniBean`을 분석하여 빈을 등록한다.

```text
mini-spring/mini-java-config
```

### 애노테이션

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniConfiguration {
}
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniBean {

    String value() default "";
}
```

### 동작 흐름

```text
설정 클래스 등록
→ @MiniBean 메서드 탐색
→ Factory Method BeanDefinition 생성
→ 메서드 파라미터를 의존성으로 해석
→ 메서드 호출
→ 결과 객체 등록
```

### 제한사항

처음에는 CGLIB 설정 클래스 프록시까지 구현하지 않아도 된다. 대신 문서에 다음 차이를 남긴다.

```text
직접 구현:
@Bean 메서드 직접 호출 시 새로운 객체

Spring Full Configuration:
CGLIB 프록시가 컨테이너 빈 반환
```

------

# 8. 의존성 주입

## 프로젝트 14: Dependency Resolution Matrix

### 목표

Spring의 후보 선택 규칙을 테스트로 정리한다.

```text
experiments/dependency-resolution-matrix
```

### 구현할 테스트

#### 생성자 선택

```text
생성자 하나, 애노테이션 없음
생성자 여러 개, @Autowired 하나
@Autowired 생성자 여러 개
기본 생성자와 optional 생성자
```

#### 후보 선택

```text
후보 1개
후보 없음
후보 여러 개
@Primary
@Qualifier
필드명과 빈 이름 일치
제네릭 타입
@Order
```

#### 주입 형태

```text
Optional<T>
ObjectProvider<T>
List<T>
Set<T>
Map<String, T>
T[]
Stream<T>
```

### 테스트 이름 예시

```java
@Test
void primaryBeanIsSelectedWhenMultipleCandidatesExist() {
}

@Test
void qualifierHasPriorityOverPrimary() {
}

@Test
void allCandidatesAreInjectedIntoList() {
}
```

### 결과물

후보 선택 규칙을 의사결정표로 만든다.

| 조건                       | 결과                |
| -------------------------- | ------------------- |
| 후보 1개                   | 해당 빈 선택        |
| 후보 여러 개 + Qualifier   | Qualifier 일치 빈   |
| 후보 여러 개 + Primary 1개 | Primary 선택        |
| 이름 일치 후보             | 이름 기반 선택 가능 |
| 최종 후보 여러 개          | 예외                |

------

## 프로젝트 15: Mini Constructor Injector

### 목표

생성자 기반 의존성 주입을 직접 구현한다.

```text
mini-spring/mini-dependency-injection
```

### 구현 단계

#### 1단계: 단일 생성자

```java
private Object instantiate(Class<?> beanClass) {
    Constructor<?> constructor =
            beanClass.getDeclaredConstructors()[0];

    Object[] arguments =
            Arrays.stream(constructor.getParameterTypes())
                    .map(this::getBean)
                    .toArray();

    return constructor.newInstance(arguments);
}
```

#### 2단계: 생성자 선택 규칙

- 생성자 한 개면 선택
- `@MiniAutowired` 생성자 선택
- 기본 생성자 fallback
- 모호하면 예외

#### 3단계: 후보 선택

- 타입 일치
- `@MiniPrimary`
- `@MiniQualifier`
- 이름 일치

#### 4단계: 선택적 주입

- `Optional<T>`
- `List<T>`
- `Provider<T>`

### 완료 조건

다음 오류를 구분해서 표현해야 한다.

```text
NoSuchBeanException
NoUniqueBeanException
UnsatisfiedDependencyException
CircularDependencyException
```

------

# 9. 순환 참조

## 프로젝트 16: Circular Dependency Laboratory

### 목표

주입 방식에 따라 순환 참조 처리 결과가 달라지는 이유를 확인한다.

```text
experiments/circular-dependency-lab
```

### 케이스

#### 생성자 순환

```java
@Component
class A {
    A(B b) {
    }
}

@Component
class B {
    B(A a) {
    }
}
```

#### setter 순환

```java
@Component
class A {

    private B b;

    @Autowired
    void setB(B b) {
        this.b = b;
    }
}
```

#### Lazy Proxy 순환

```java
A(@Lazy B b) {
}
```

#### AOP 프록시가 포함된 순환

```text
A → B
B → A
A에 @Transactional 적용
```

### 관찰 대상

```text
singletonObjects
earlySingletonObjects
singletonFactories
singletonsCurrentlyInCreation
```

### 주의

세 단계 캐시를 그대로 복제하는 것이 목적은 아니다. 다음 질문에 답하는 것이 핵심이다.

- 왜 완성되지 않은 빈을 노출해야 하는가?
- 왜 생성자 순환 참조는 조기 노출로 해결하기 어려운가?
- 프록시가 필요한 빈은 원본을 조기 노출하면 왜 문제가 되는가?

------

## 프로젝트 17: Mini Cycle Detector

### 목표

순환 참조를 해결하기보다 정확하게 탐지한다.

```java
private final Set<String> beansCurrentlyInCreation =
        new HashSet<>();

private Object createBean(String beanName) {
    if (!beansCurrentlyInCreation.add(beanName)) {
        throw new CircularDependencyException(beanName);
    }

    try {
        return doCreateBean(beanName);
    } finally {
        beansCurrentlyInCreation.remove(beanName);
    }
}
```

### 추가 구현

생성 경로를 함께 기록한다.

```text
orderService
→ paymentService
→ orderService
```

이를 예외 메시지로 표현한다.

```text
Circular dependency detected:
orderService -> paymentService -> orderService
```

실무 프레임워크에서 진단 가능성이 중요한 이유를 이해할 수 있다.

------

# 10. Spring AOP

## 프로젝트 18: Proxy Playground

### 목표

JDK Dynamic Proxy와 CGLIB 프록시를 직접 비교한다.

```text
experiments/proxy-playground
```

### 실험 대상

- 인터페이스가 있는 클래스
- 인터페이스가 없는 클래스
- final 클래스
- final 메서드
- private 메서드
- equals/hashCode
- 실제 클래스 타입 캐스팅
- 프록시 내부 필드
- self-invocation

### 출력 항목

```java
System.out.println(bean.getClass());
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean));
```

------

## 프로젝트 19: Mini AOP Framework

### 목표

인터셉터 체인의 핵심 구조를 직접 구현한다.

```text
mini-spring/mini-aop
```

### 핵심 인터페이스

```java
public interface MethodInterceptor {

    Object invoke(MethodInvocation invocation)
            throws Throwable;
}
public interface MethodInvocation {

    Method getMethod();

    Object[] getArguments();

    Object getTarget();

    Object proceed() throws Throwable;
}
```

### 구현체

```java
public final class ReflectiveMethodInvocation
        implements MethodInvocation {

    private final Object target;
    private final Method method;
    private final Object[] arguments;
    private final List<MethodInterceptor> interceptors;

    private int index = -1;

    @Override
    public Object proceed() throws Throwable {
        if (++index == interceptors.size()) {
            return method.invoke(target, arguments);
        }

        return interceptors.get(index).invoke(this);
    }
}
```

### 구현할 Advice

- 실행 시간 측정
- 메서드 호출 로깅
- 재시도
- 권한 검사
- 예외 변환
- 호출 횟수 집계

### 인터셉터 실행 예

```text
LoggingInterceptor before
→ AuthorizationInterceptor before
→ TimingInterceptor before
→ Target Method
→ TimingInterceptor after
→ AuthorizationInterceptor after
→ LoggingInterceptor after
```

------

## 프로젝트 20: Annotation-Based Auto Proxy Creator

### 목표

애노테이션이 붙은 메서드를 가진 빈을 자동으로 프록시화한다.

```text
mini-spring/mini-auto-proxy
```

### 구현 대상

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniTransactional {
}
```

```text
BeanPostProcessor
→ 빈 클래스 메서드 탐색
→ @MiniTransactional 발견
→ ProxyFactory로 프록시 생성
→ TransactionInterceptor 연결
```

### 학습 포인트

- AOP는 빈 생성 후처리와 연결됨
- Pointcut과 Advice의 분리
- Advisor라는 조합 객체가 필요한 이유
- 모든 빈을 프록시로 만들지 않고 후보를 판별하는 이유

------

# 11. 트랜잭션

## 프로젝트 21: Transaction Propagation Playground

### 목표

전파 속성과 실제 Connection의 관계를 확인한다.

```text
experiments/transaction-propagation-playground
```

### 도메인

간단한 주문과 결제 이력을 사용한다.

```text
orders
payment_history
audit_log
```

### 서비스 구성

```java
@Service
public class OrderService {

    private final PaymentService paymentService;
    private final AuditService auditService;

    @Transactional
    public void placeOrder() {
        saveOrder();
        paymentService.pay();
        auditService.record();
    }
}
```

### 실험 조합

```text
외부 REQUIRED
내부 REQUIRED

외부 REQUIRED
내부 REQUIRES_NEW

외부 REQUIRED
내부 NESTED

외부 REQUIRED
내부 NOT_SUPPORTED
```

### 각 테스트에서 기록할 항목

- 현재 스레드 이름
- Connection identity
- autoCommit
- transaction active 여부
- rollback-only 여부
- 최종 DB 데이터

Connection 식별 예:

```java
Connection connection =
        DataSourceUtils.getConnection(dataSource);

System.out.println(
        System.identityHashCode(connection)
);
```

### 테스트 시나리오

- 내부 성공, 외부 성공
- 내부 실패, 예외 전파
- 내부 실패, 외부에서 catch
- 내부 트랜잭션 rollback-only
- `UnexpectedRollbackException`
- `REQUIRES_NEW` 내부 커밋 후 외부 롤백
- 비동기 호출
- self-invocation

------

## 프로젝트 22: Mini Transaction Manager

### 목표

트랜잭션 관리의 최소 구조를 구현한다.

```text
mini-spring/mini-transaction
```

### 인터페이스

```java
public interface MiniTransactionManager {

    TransactionStatus begin();

    void commit(TransactionStatus status);

    void rollback(TransactionStatus status);
}
```

### JDBC 구현체

```java
public final class JdbcTransactionManager
        implements MiniTransactionManager {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> connectionHolder =
            new ThreadLocal<>();

    @Override
    public TransactionStatus begin() {
        // Connection 획득
        // autoCommit false
        // ThreadLocal 바인딩
    }
}
```

### TransactionInterceptor

```java
public final class TransactionInterceptor
        implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation)
            throws Throwable {

        TransactionStatus status =
                transactionManager.begin();

        try {
            Object result = invocation.proceed();
            transactionManager.commit(status);
            return result;
        } catch (Throwable throwable) {
            transactionManager.rollback(status);
            throw throwable;
        }
    }
}
```

### 발전 단계

```text
1단계: 항상 새 트랜잭션
2단계: ThreadLocal 기반 기존 트랜잭션 참여
3단계: REQUIRED
4단계: REQUIRES_NEW
5단계: rollback-only
6단계: Synchronization callback
```

------

## 프로젝트 23: Transactional Outbox Sample

### 목표

Spring 트랜잭션이 실제 시스템 설계에서 어떤 경계를 보장하는지 확인한다.

```text
sample-app/transactional-outbox-order
```

### 흐름

```text
주문 저장
+
Outbox 이벤트 저장
→ 동일 DB 트랜잭션

별도 퍼블리셔
→ Outbox 조회
→ 메시지 발행
→ 발행 완료 처리
```

### 학습 포인트

- DB 트랜잭션의 보장 범위
- 메시지 브로커와 DB 간 원자성 문제
- `@TransactionalEventListener`
- `AFTER_COMMIT`
- Outbox 패턴
- 멱등성

단순 Framework 내부 학습을 실제 백엔드 아키텍처와 연결하기 좋은 종합 과제다.

------

# 12. Spring MVC

## 프로젝트 24: DispatcherServlet Trace Application

### 목표

HTTP 요청이 Servlet에서 Controller까지 도달하는 전체 흐름을 추적한다.

```text
experiments/dispatcher-servlet-trace
```

### 구현 대상 API

```http
GET /users/{id}?detail=true
POST /users
GET /users/me
```

### 컨트롤러

```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    public UserResponse find(
            @PathVariable Long id,
            @RequestParam boolean detail
    ) {
        return new UserResponse(id, detail);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.ok(...);
    }
}
```

### 브레이크포인트

```text
HttpServlet#service
FrameworkServlet#processRequest
DispatcherServlet#doService
DispatcherServlet#doDispatch
RequestMappingHandlerMapping#getHandlerInternal
RequestMappingHandlerAdapter#handleInternal
RequestMappingHandlerAdapter#invokeHandlerMethod
InvocableHandlerMethod#invokeForRequest
```

### 기록할 내용

- HandlerExecutionChain
- 선택된 HandlerMethod
- 선택된 HandlerAdapter
- 등록된 Interceptor
- ArgumentResolver 목록
- ReturnValueHandler 목록
- HttpMessageConverter 목록

------

## 프로젝트 25: Custom Argument Resolver

### 목표

요청 정보를 사용자 정의 객체로 변환한다.

```text
spring-extensions/current-user-argument-resolver
```

### 애노테이션

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

### 컨트롤러

```java
@GetMapping("/me")
public UserResponse me(
        @CurrentUser AuthenticatedUser user
) {
    return UserResponse.from(user);
}
```

### Resolver

```java
public class CurrentUserArgumentResolver
        implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(
            MethodParameter parameter
    ) {
        return parameter.hasParameterAnnotation(
                CurrentUser.class
        );
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        return extractUser(webRequest);
    }
}
```

### 확장 과제

- 헤더에서 사용자 정보 추출
- JWT에서 사용자 정보 추출
- 필수·선택 사용자 구분
- 인증 실패 예외
- 테스트용 MockMvc 구성

------

## 프로젝트 26: Custom Return Value Handler

### 목표

Controller 반환값을 공통 API 응답으로 감싼다.

```text
spring-extensions/api-response-handler
```

### 목표 형태

컨트롤러에서는 다음처럼 반환한다.

```java
@GetMapping("/{id}")
public UserResponse findUser(@PathVariable Long id) {
    return service.find(id);
}
```

실제 응답은 다음과 같이 변환한다.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Soren"
  },
  "timestamp": "..."
}
```

### 비교 대상

- `HandlerMethodReturnValueHandler`
- `ResponseBodyAdvice`
- `HttpMessageConverter`

각 확장 지점의 책임 차이를 정리해야 한다.

------

## 프로젝트 27: Mini Web MVC

### 목표

Servlet API 위에 단순한 MVC 프레임워크를 구현한다.

```text
mini-spring/mini-webmvc
```

### 구현 단계

#### 1단계: Front Controller

```java
public class MiniDispatcherServlet
        extends HttpServlet {

    @Override
    protected void service(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
    }
}
```

#### 2단계: HandlerMapping

```java
public interface HandlerMapping {

    HandlerMethod getHandler(
            HttpServletRequest request
    );
}
```

#### 3단계: HandlerAdapter

```java
public interface HandlerAdapter {

    boolean supports(Object handler);

    Object handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    );
}
```

#### 4단계: ArgumentResolver

- String
- primitive
- PathVariable
- RequestParam
- RequestBody

#### 5단계: ReturnValueHandler

- String
- JSON 객체
- status code
- ResponseEntity 유사 객체

#### 6단계: ExceptionResolver

```java
public interface HandlerExceptionResolver {

    boolean resolve(
            Throwable exception,
            HttpServletResponse response
    );
}
```

### 완료 흐름

```text
HTTP Request
→ MiniDispatcherServlet
→ HandlerMapping
→ HandlerAdapter
→ ArgumentResolver
→ Controller Method
→ ReturnValueHandler
→ JSON Response
```

------

# 13. 예외 처리와 검증

## 프로젝트 28: Error Handling Pipeline

### 목표

Spring MVC 예외 처리 우선순위를 검증한다.

```text
experiments/mvc-exception-pipeline
```

### 구현 대상

- 컨트롤러 내부 `@ExceptionHandler`
- `@ControllerAdvice`
- 여러 Advice의 `@Order`
- `ResponseStatusException`
- `@ResponseStatus`
- 타입 변환 실패
- JSON 역직렬화 실패
- Bean Validation 실패
- 존재하지 않는 Handler
- 지원하지 않는 HTTP Method

### 결과물

다음 형식의 표를 작성한다.

| 예외 발생 위치  | 처리 컴포넌트                     | 최종 응답 |
| --------------- | ---------------------------------- | --------- |
| Controller 내부 | ExceptionHandlerExceptionResolver | 400       |
| 파라미터 변환   | DefaultHandlerExceptionResolver   | 400       |
| JSON 파싱       | HttpMessageConverter 계층         | 400       |
| URL 없음        | 설정에 따라 다름                  | 404       |

------

# 14. 이벤트 시스템

## 프로젝트 29: Application Event Bus

### 목표

Spring 이벤트 발행과 리스너 실행 방식을 이해한다.

```text
experiments/application-event-lab
```

### 실험 대상

- 동기 이벤트
- `@EventListener`
- `ApplicationListener`
- `@Async`
- `@Order`
- 조건부 이벤트
- 트랜잭션 이벤트
- 리스너 예외
- 부모·자식 컨텍스트

### 이벤트

```java
public record OrderCompletedEvent(
        Long orderId
) {
}
```

### 발행

```java
eventPublisher.publishEvent(
        new OrderCompletedEvent(orderId)
);
```

### 비교 대상

```text
ApplicationEventPublisher
ApplicationEventMulticaster
SimpleApplicationEventMulticaster
TransactionalApplicationListener
```

------

## 프로젝트 30: Mini Event Multicaster

### 구현 대상

```java
public interface EventListener<E> {

    void onEvent(E event);
}
public final class EventMulticaster {

    private final Map<Class<?>, List<EventListener<?>>>
            listeners = new HashMap<>();

    public void publish(Object event) {
        // 이벤트 타입에 맞는 리스너 호출
    }
}
```

### 발전 과제

- 동기·비동기 실행
- 리스너 순서
- 상위 이벤트 타입
- 리스너 예외 정책
- 트랜잭션 커밋 후 실행

------

# 15. Spring Boot

## 프로젝트 31: SpringApplication Lifecycle Inspector

### 목표

Boot 애플리케이션 시작 이벤트와 컨텍스트 생성 과정을 기록한다.

```text
experiments/spring-application-lifecycle
```

### 관찰 이벤트

```text
ApplicationStartingEvent
ApplicationEnvironmentPreparedEvent
ApplicationContextInitializedEvent
ApplicationPreparedEvent
ApplicationStartedEvent
AvailabilityChangeEvent
ApplicationReadyEvent
ApplicationFailedEvent
```

### 추가 구현

각 이벤트에서 다음을 출력한다.

- 이벤트 시각
- Environment 사용 가능 여부
- Context 사용 가능 여부
- Bean 조회 가능 여부
- 웹 서버 시작 여부

------

## 프로젝트 32: Custom AutoConfiguration

### 목표

Spring Boot의 자동 설정 원리를 직접 사용해본다.

```text
spring-extensions/request-observation-starter
```

### 모듈 구조

```text
request-observation/
├── request-observation-core
├── request-observation-autoconfigure
└── request-observation-spring-boot-starter
```

### 기능

- HTTP 요청 실행 시간 기록
- 특정 경로 제외
- slow request 기준 설정
- 사용자 빈이 있으면 자동 설정 비활성화
- WebMVC가 있을 때만 활성화

### 설정

```yaml
request-observation:
  enabled: true
  slow-threshold: 500ms
  exclude-paths:
    - /actuator/**
```

### 자동 설정

```java
@AutoConfiguration
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(
        RequestObservationProperties.class
)
public class RequestObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestObservationInterceptor
            requestObservationInterceptor(
                    RequestObservationProperties properties
            ) {
        return new RequestObservationInterceptor(properties);
    }
}
```

### 반드시 작성할 테스트

```text
기본 설정에서 빈 생성
enabled=false이면 생성하지 않음
사용자 정의 빈이 있으면 자동 설정이 물러남
WebMVC가 없으면 생성하지 않음
프로퍼티 바인딩 검증
```

`ApplicationContextRunner`를 사용하면 좋다.

------

# 16. 종합 프로젝트 추천

각 파트를 따로 구현한 후에는 하나의 애플리케이션으로 통합해야 한다.

## 종합 프로젝트: Mini Order Platform

```text
sample-app/mini-order-platform
```

### 도메인

```text
회원
상품
주문
결제
알림
감사 로그
```

### 적용할 학습 요소

#### IoC

- 전략 구현체 등록
- 타입별 플러그인 구성
- `@Qualifier`
- 컬렉션 주입

#### 빈 생명주기

- 외부 클라이언트 초기화
- 리소스 종료
- 커스텀 BeanPostProcessor

#### AOP

- 실행 시간 측정
- 권한 검사
- 감사 로그
- 재시도
- 멱등성 검사

#### 트랜잭션

- 주문과 Outbox 저장
- 결제 이력 분리
- `REQUIRES_NEW`
- rollback-only 실험

#### MVC

- 커스텀 인증 사용자 Resolver
- 공통 응답 처리
- 예외 처리
- 커스텀 Converter

#### 이벤트

- 주문 완료 이벤트
- 알림 발송
- Outbox 발행
- 트랜잭션 커밋 이후 처리

#### Boot

- 결제 클라이언트 AutoConfiguration
- 알림 플러그인 Starter
- 요청 관측 Starter

------

# 17. 우선순위에 따른 구현 목록

모든 프로젝트를 구현하면 범위가 상당히 커진다. 따라서 세 단계로 나누는 것이 좋다.

## 필수 구현

Spring 내부 구조를 이해하는 데 직접적인 프로젝트다.

```text
1. Mini BeanFactory
2. BeanDefinition Inspector
3. Bean Lifecycle Recorder
4. Mini Bean Lifecycle Pipeline
5. Mini Component Scanner
6. Dependency Resolution Matrix
7. Mini Constructor Injector
8. Proxy Playground
9. Mini AOP Framework
10. Transaction Propagation Playground
11. Mini Transaction Manager
12. DispatcherServlet Trace
13. Custom Argument Resolver
14. Mini Web MVC
15. Custom AutoConfiguration
```

## 심화 구현

확장 포인트와 경계 조건을 이해하기 위한 프로젝트다.

```text
1. Dynamic Client Registry
2. Context Refresh Visualizer
3. Configuration Proxy Experiment
4. Circular Dependency Laboratory
5. Annotation-Based Auto Proxy Creator
6. MVC Exception Pipeline
7. Application Event Laboratory
8. Transactional Outbox
```

## 포트폴리오 구현

실제 백엔드 프로젝트로 보여주기 좋은 대상이다.

```text
1. Request Observation Starter
2. Plugin Auto Discovery System
3. Transactional Outbox Order
4. Mini Order Platform
5. Mini Spring Framework
```

------

# 18. 현실적인 구현 순서

16주 과정이라면 다음 순서가 적합하다. (주차별 학습 주제는 [`01-roadmap.md`](./01-roadmap.md) 참고)

| 주차      | 구현 프로젝트                               |
| --------- | -------------------------------------------- |
| 1주       | BeanFactory Lab                             |
| 2주       | Mini BeanFactory                            |
| 3주       | BeanDefinition Inspector                    |
| 4주       | Bean Lifecycle Recorder                     |
| 5주       | Mini Bean Lifecycle Pipeline                |
| 6주       | BeanFactoryPostProcessor 실험               |
| 7주       | Mini Component Scanner                      |
| 8주       | Configuration Proxy Experiment              |
| 9주       | Dependency Resolution Matrix                |
| 10주      | Mini Constructor Injector 및 Cycle Detector |
| 11주      | Proxy Playground                            |
| 12주      | Mini AOP Framework                          |
| 13주      | Transaction Propagation Playground          |
| 14주      | Mini Transaction Manager                    |
| 15주      | DispatcherServlet Trace와 Argument Resolver |
| 16주      | Mini Web MVC                                |
| 선택 17주 | Application Event Laboratory                |
| 선택 18주 | Custom AutoConfiguration                    |
| 선택 19주 | Custom Starter                              |
| 선택 20주 | Mini Order Platform 통합                    |

------

# 19. 각 프로젝트의 완료 기준

프로젝트가 단순 예제 코드로 끝나지 않도록 다음 조건을 공통 완료 기준으로 둔다.

## 코드

- 정상 흐름 테스트
- 실패 흐름 테스트
- 경계 조건 테스트
- 로그를 통한 내부 상태 확인
- Spring 구현과 축소 구현 분리

## 문서

```text
문제 정의
공식 문서의 설명
예상 동작
최소 재현 코드
핵심 인터페이스
실제 호출 스택
Spring의 설계 방식
직접 구현한 방식
두 구현의 차이
한계와 개선 방향
```

## 다이어그램

각 프로젝트마다 최소한 다음 중 하나를 작성한다.

- 클래스 다이어그램
- 시퀀스 다이어그램
- 상태 전이도
- 빈 생명주기 흐름도
- 요청 처리 흐름도

## 설명 가능성

다음 세 질문에 답할 수 있어야 완료된 것이다.

```text
이 기능은 어떤 문제를 해결하는가?

Spring은 어떤 추상화와 실행 순서로 해결하는가?

내 축소 구현은 무엇을 생략했고,
그로 인해 어떤 문제가 발생할 수 있는가?
```

가장 먼저 구현할 대상은 **Mini BeanFactory → Bean Lifecycle Recorder → Mini Constructor Injector → Mini AOP → Mini Transaction Manager → Mini Web MVC**다. 이 여섯 프로젝트를 연결하면 Spring Framework의 핵심 구조인 **객체 생성, 확장, 프록시, 트랜잭션, 웹 요청 처리**가 하나의 흐름으로 이어진다.
