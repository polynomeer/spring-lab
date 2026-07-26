# BeanFactoryPostProcessor — BeanDefinition을 고치는 시점과 그 시점이 갈리는 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 5주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 8(Configuration Property Rewriter)에 대응하는 분석 문서다. 카탈로그가 "5. BeanFactoryPostProcessor와 BeanPostProcessor"로 두 프로젝트를 묶어 두었으므로, 프로젝트 9(Method Timing BeanPostProcessor) 1단계 결과도 13번에 이어 붙였다.

## 1. 이번 질문

`BeanFactoryPostProcessor`로 `BeanDefinition`을 고치는 실험에서, `BeanDefinitionRegistryPostProcessor`(등록소 구조 자체를 바꿀 수 있는 것)는 일반 `BeanFactoryPostProcessor`와 실행 시점이 어떻게 다른가? 그리고 **같은 `BeanDefinitionRegistryPostProcessor`라도 등록 방법(빈으로 등록 vs `context.addBeanFactoryPostProcessor()`로 직접 추가)에 따라 실행 시점이 달라질 수 있는가?**

두 번째 질문은 원래 계획에 없었다 — 첫 번째 질문에 답하려고 실험을 짜다가 실제로 버그를 만나면서 생겼다.

## 2. 공식 문서 요약

- `BeanFactoryPostProcessor`는 `BeanDefinition`에 개입하고, `BeanPostProcessor`는 생성된 빈 인스턴스에 개입한다 — 4주차까지 계속 확인해 온 구분이다.
- `BeanDefinitionRegistryPostProcessor`는 `BeanFactoryPostProcessor`를 확장해서 `BeanDefinition`을 등록·제거하는 등 레지스트리 구조 자체를 바꿀 수 있다.
- `ConfigurationClassPostProcessor`(2주차에서 이미 다룸)가 `BeanDefinitionRegistryPostProcessor`의 대표 구현체다 — `@ComponentScan`과 `@Bean` 처리 자체가 이 메커니즘으로 이뤄진다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `BeanFactoryPostProcessor`를 어떤 방식으로 등록하든(빈으로 등록하든, `context.addBeanFactoryPostProcessor()`로 직접 넘기든) 실행 시점은 같을 것이라 예상했다 — **정면으로 틀렸다.**
- `BeanDefinitionRegistryPostProcessor`는 일반 `BeanFactoryPostProcessor`의 "조금 더 강력한 버전" 정도로 생각했다 — 실제로는 아예 별도의, 훨씬 이른 단계 전체가 있었다.
- 컴포넌트 스캔으로 등록된 빈도 우리가 만든 후처리기가 문제없이 볼 수 있을 것이라 예상했다(→ 결과적으로는 맞았다) — 다만 "제대로 등록했을 때만" 이라는 전제 조건이 있다는 걸 몰랐다.

## 4. 최소 재현 코드

[`spring-extensions/configuration-property-rewriter`](../../spring-extensions/configuration-property-rewriter)

버그가 있었던 최초 버전:
```java
context.register(RewriterConfig.class);  // @ComponentScan으로 ExcludedService 등을 찾는 설정
context.addBeanFactoryPostProcessor(new ExclusionRegistryPostProcessor());  // @Excluded 빈 제거
context.refresh();
// → excludedService가 여전히 등록돼 있음 (제거 실패, 예외도 없이 조용히)
```

수정한 버전:
```java
context.register(RewriterConfig.class);
context.registerBean("exclusionRegistryPostProcessor", ExclusionRegistryPostProcessor.class,
        () -> new ExclusionRegistryPostProcessor());
context.refresh();
// → excludedService가 정상적으로 제거됨
```

전체 코드: [`ConfigRewriterLab.java`](../../spring-extensions/configuration-property-rewriter/src/main/java/lab/ext/rewriter/ConfigRewriterLab.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `BeanFactoryPostProcessor` | `BeanDefinition` 메타데이터(scope, lazy, primary, role, property value 등)를 수정 |
| `BeanDefinitionRegistryPostProcessor` | `BeanFactoryPostProcessor` + 레지스트리 구조 자체(등록·제거)를 변경 |
| `ConfigurationClassPostProcessor` | `BeanDefinitionRegistryPostProcessor`의 대표 구현체. `@ComponentScan`/`@Bean` 처리를 실제로 수행 |
| `PostProcessorRegistrationDelegate` | `refresh()`의 `invokeBeanFactoryPostProcessors` 단계 전체를 조율하는 static 유틸리티 |
| `AbstractApplicationContext#getBeanFactoryPostProcessors()` | `context.addBeanFactoryPostProcessor()`로 미리 추가해 둔, **빈이 아닌** 후처리기 목록 |
| `GenericApplicationContext#registerBean` | 미리 만든 인스턴스를 실제 빈으로 등록 (Supplier 기반) |

## 6. 호출 흐름

버그의 원인을 그림으로: [`diagrams/registration-order-bug.md`](diagrams/registration-order-bug.md)

```text
invokeBeanFactoryPostProcessors(beanFactory, beanFactoryPostProcessors)
  1단계: beanFactoryPostProcessors 순회 (= context.addBeanFactoryPostProcessor()로 넘긴 것들)
         → BDRPP면 postProcessBeanDefinitionRegistry() 즉시 호출     ← 우리 버그가 여기서 발생
  2단계: 빈으로 등록된 BeanDefinitionRegistryPostProcessor 탐색
         PriorityOrdered 그룹  → ConfigurationClassPostProcessor (@ComponentScan/@Bean 처리, 여기서 스캔됨)
         Ordered 그룹
         나머지(reiterate) 그룹 → 우리가 빈으로 등록한 ExclusionRegistryPostProcessor는 여기
  3단계: 1·2단계에서 발견된 모든 BDRPP의 postProcessBeanFactory() 호출
  4단계: 나머지 일반 BeanFactoryPostProcessor의 postProcessBeanFactory() 호출
         → BeanDefinitionAnnotationRewriter는 여기 (등록 방법과 무관하게 항상 마지막)
```

`addBeanFactoryPostProcessor()`로 넘긴 `BeanDefinitionRegistryPostProcessor`는 **1단계**에서 실행되는데, `@ComponentScan`을 실제로 수행하는 `ConfigurationClassPostProcessor`는 **2단계**에서 실행된다 — 순서가 뒤바뀌어 있었다.

## 7. 브레이크포인트

이번 주제는 로그·소스 확인(8·9번)만으로 검증했다. 다음은 추적 후보다.

```text
org.springframework.context.support.PostProcessorRegistrationDelegate#invokeBeanFactoryPostProcessors
org.springframework.context.annotation.ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry
```

## 8. 런타임 관찰

**버그 재현 시점의 실제 출력** (`ConfigRewriterLab` 최초 버전):
```text
excludedService still registered? = true   ← 제거가 전혀 안 됨
```
나머지 6개 실험(scope 변경, lazy 변경, property 추가, primary 변경, role 변경, 타입 기반 primary 해석)은 처음부터 예상대로 동작했다 — 전부 `postProcessBeanFactory()`(4단계) 하나에서만 이뤄지는 작업이라, 등록 방법과 무관하게 항상 registry 변경이 다 끝난 뒤에 실행되기 때문이다.

**수정 후 최종 결과** ([`ConfigRewriterTest`](../../spring-extensions/configuration-property-rewriter/src/test/java/lab/ext/rewriter/ConfigRewriterTest.java)로 고정):

| 실험 | 결과 |
| --- | --- |
| `@ForcePrototype` | `getScope() == "prototype"`, `getBean()` 호출마다 다른 인스턴스 |
| `@ForceLazy` | `isLazyInit() == true`, `refresh()` 동안 생성자 호출 0회 |
| `@DefaultChannel("email")` | 프로퍼티가 비어 있을 때만 `channel = "email"`로 채워짐 |
| `@Excluded` | `containsBeanDefinition() == false`, `getBean()` → `NoSuchBeanDefinitionException` |
| `@ForcePrimary` | `isPrimary() == true`, 타입 기반 조회가 모호성 없이 이 빈으로 해석됨 |
| `@SupportRole` | `getRole() == BeanDefinition.ROLE_SUPPORT` |
| (등록 순서 증명) | `BeanDefinitionAnnotationRewriter`가 빈 이름을 순회할 때 이미 `excludedService`는 목록에 없음 |

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스로 확인했다 — `spring-context/src/test/java/org/springframework/context/support/BeanFactoryPostProcessorTests.java`.

`beanDefinitionRegistryPostProcessorRegisteringAnother()`, `prioritizedBeanDefinitionRegistryPostProcessorRegisteringAnother()`는 하나의 `BeanDefinitionRegistryPostProcessor`가 `postProcessBeanDefinitionRegistry()` 안에서 **또 다른** `BeanFactoryPostProcessor` 빈을 새로 등록해도, 그 새로 등록된 것까지 전부 발견돼서 `postProcessBeanFactory()`가 호출되는 것을 검증한다 — 이게 바로 6번에서 본 "reiterate" 루프(우리가 읽은 `while (reiterate) { ... }`)가 실제로 동작한다는 증거다. 이 테스트들 자체는 우리가 겪은 시나리오(스캔 이전 vs 이후)를 직접 다루지는 않지만, 같은 함수(`PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors`)가 매 단계마다 "지금까지 발견된 걸 전부 처리하고 나서 다음 그룹으로 넘어간다"는 원칙으로 동작한다는 걸 보여준다 — `context.addBeanFactoryPostProcessor()`로 넘긴 것들이 왜 "가장 먼저 발견된 그룹"으로 취급되는지도 같은 원칙의 연장선이다.

지난주(4주차) 문서에서 이미 인용한 `AbstractApplicationContext.invokeBeanFactoryPostProcessors()`의
```java
PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());
```
한 줄이 이번 버그의 정확한 원인이다 — `getBeanFactoryPostProcessors()`가 `addBeanFactoryPostProcessor()`로 쌓아 둔 리스트를 그대로 반환하고, 이게 `PostProcessorRegistrationDelegate`의 첫 번째 루프(6번의 1단계)로 그대로 들어간다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-container`(project 7)는 `BeanPostProcessor`(인스턴스 후처리)는 갖췄지만, `BeanFactoryPostProcessor`(정의 단계 후처리)는 아직 없다. 이번 실험에서 겪은 "등록 방법에 따라 실행 시점이 갈리는" 문제를 재현하려면 최소한 다음이 필요하다.

- `BeanFactoryPostProcessor` 인터페이스와 `addBeanFactoryPostProcessor`에 해당하는 등록 지점
- `BeanDefinition` 등록 시점과 후처리기 실행 시점을 분리하는 별도 단계 (지금 `SimpleBeanFactory`는 그런 단계 자체가 없다 — `registerBeanDefinition()`을 호출하면 그걸로 끝이고, "등록이 다 끝난 뒤 한꺼번에 후처리"하는 개념이 없다)

이 격차는 project 7이 "생성된 인스턴스"만 다뤘기 때문에 생긴 것이라, 다음에 `mini-container`를 확장한다면 `refresh()`에 해당하는 상위 단계(3주차에서 다룬 "여러 확장 지점을 조율하는 컨테이너 초기화 절차")를 먼저 만들어야 자연스럽게 이어진다.

## 11. Spring 설계 의도

- **왜 `addBeanFactoryPostProcessor()`로 등록한 것을 가장 먼저 실행하는가**: 이 메서드는 애플리케이션 코드가 "컨텍스트가 부트스트랩되기도 전에, 반드시 제일 먼저 실행돼야 하는" 후처리기를 등록하는 탈출구다. 예를 들어 `PropertySourcesPlaceholderConfigurer`처럼 다른 모든 설정 해석의 전제 조건이 되는 처리기는 컴포넌트 스캔 결과에 의존해서는 안 된다. 다만 이번에 확인했듯, 이 "제일 먼저"라는 보장은 **컴포넌트 스캔 결과에 의존하는 로직**에는 함정이 된다 — 스캔으로 등록될 빈을 대상으로 무언가 하려면 오히려 빈으로 등록해서 스캔 **이후** 단계로 밀어 넣어야 한다.
- **`BeanDefinitionRegistryPostProcessor`를 별도 인터페이스로 분리한 이유**: 레지스트리 구조 변경(등록·제거)과 메타데이터 수정(scope, lazy 등)은 안전성 요구 수준이 다르다. 구조 변경은 이후의 모든 처리기가 "완성된 빈 목록"을 전제로 동작할 수 있도록 가장 먼저 끝나야 하고, 메타데이터 수정은 그 목록이 확정된 뒤에 이뤄져야 앞뒤가 맞는다. 두 책임을 인터페이스로 나누고 실행 단계까지 분리하면, 각 처리기 작성자가 "내가 지금 어느 단계에 있는지"를 인터페이스 선택만으로 명확히 할 수 있다.

## 12. 결론 (예상과 실제의 차이)

- 예상과 정면으로 달랐던 것: `BeanFactoryPostProcessor`의 등록 **방법**(빈 vs `addBeanFactoryPostProcessor`)이 실행 **시점**을 바꾼다. 같은 인터페이스, 같은 코드라도 어떻게 컨테이너에 알렸는지에 따라 컴포넌트 스캔보다 먼저 실행될 수도, 나중에 실행될 수도 있다.
- 이 문제는 사전 설계가 아니라 **실제로 겪은 버그**를 통해 발견했다 — `docs/plan/00-methodology.md`의 순환("최소 예제 → 실행 → 관찰")이 문서를 쓰기 전부터 이미 효과가 있었던 사례다.
- 예상대로였던 것: registry 구조가 확정된 뒤에만 동작하는 나머지 6개 실험(scope/lazy/property/primary/role/타입 해석)은 등록 방법과 무관하게 처음부터 안정적으로 동작했다 — 이 차이가 "구조 변경"과 "메타데이터 수정"을 굳이 다른 인터페이스로 나눈 이유(11번)를 몸으로 확인시켜 준 셈이다.
- 새로 열린 질문: `mini-container`에 `BeanFactoryPostProcessor`를 추가할 때, 이번에 겪은 "등록 시점 vs 실행 시점" 문제까지 재현할지는 아직 정하지 않았다.

------

## 13. 추가 실험: BeanPostProcessor로 빈 자체를 교체하기 (프로젝트 9, 1단계)

`BeanFactoryPostProcessor`가 "정의(BeanDefinition)"를 고치는 지점이라면, `BeanPostProcessor`는 "생성된 인스턴스"에 개입하는 지점이다 — 5주차 로드맵이 처음부터 강조한 구분이다. [`spring-extensions/method-timing-post-processor`](../../spring-extensions/method-timing-post-processor)에서 이 구분을 가장 극단적인 형태로 확인했다: `BeanPostProcessor`는 인스턴스를 "고치는" 정도가 아니라 **완전히 다른 객체로 통째로 교체**할 수 있다.

### 최소 재현 코드

```java
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    Class<?>[] interfaces = bean.getClass().getInterfaces();
    if (interfaces.length == 0 || !anyMethodAnnotated(interfaces)) {
        return bean;
    }
    return Proxy.newProxyInstance(
            bean.getClass().getClassLoader(), interfaces, new TimingInvocationHandler(bean));
}
```

`@MeasureTime`이 붙은 인터페이스 메서드가 있으면 원본 대신 `java.lang.reflect.Proxy`를 반환한다 — 컨테이너의 싱글턴 캐시에 최종적으로 저장되는 객체가 `OrderServiceImpl`이 아니라 그 프록시다.

### 실제로 확인한 것

- `context.getBean(OrderService.class)`가 반환한 객체는 `Proxy.isProxyClass(...)`가 `true`이고 `OrderServiceImpl`의 인스턴스가 **아니다**.
- `@MeasureTime`이 붙은 메서드(`placeOrder`)만 측정 로그에 남고, 같은 인터페이스의 다른 메서드(`cachedLookup`)는 정상 동작하지만 측정되지 않는다.
- `@MeasureTime`은 **구현 클래스가 아니라 인터페이스 메서드**에 붙여야 한다 — JDK 동적 프록시는 인터페이스의 `Method` 객체로 디스패치하므로, 구현체 오버라이드에만 애노테이션이 있으면 `InvocationHandler`가 절대 볼 수 없다. 처음에 이 사실을 모르고 짰다면 조용히 아무것도 측정되지 않았을 것이다.
- 인터페이스가 없는 `LegacyReport`는 `@MeasureTime`이 붙어 있어도 JDK 프록시로 감쌀 수 없다 — 원본 그대로 통과하고 아무것도 측정되지 않는다. 이 한계가 왜 Spring이 (인터페이스가 없을 때) CGLIB로 넘어가는지의 실질적인 이유다.

### 공식 소스와의 연결

이 패턴이 장난감 단순화가 아니라는 근거를 실제 Spring AOP 코어에서 찾았다 — `spring-aop`의 `AbstractAutoProxyCreator`(`@Transactional`, `@Async`, `@Aspect` 전부를 가능하게 하는 클래스)도 정확히 같은 지점에서 같은 일을 한다.
```java
// org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        if (this.earlyBeanReferences.remove(cacheKey) != bean) {
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
    }
    return bean;
}
```
`wrapIfNecessary()`가 (JDK 프록시 또는 CGLIB로) 조건에 맞는 빈을 프록시로 감싸 돌려준다는 점에서 우리 `MethodTimingBeanPostProcessor`와 구조가 동일하다 — 카탈로그가 이 프로젝트의 학습 포인트로 제시한 "AOP가 IoC 컨테이너 위에서 동작하는 이유"에 대한 직접적인 답이다: **Spring AOP는 별도의 특별한 메커니즘이 아니라, `BeanPostProcessor`라는 이미 있는 확장 지점 하나를 활용해서 구현된 기능**이다.

### 남겨둔 것

카탈로그는 이 프로젝트를 4단계로 제시한다: JDK Dynamic Proxy(완료) → Spring `ProxyFactory` → `Pointcut`+`Advisor` → 자동 프록시 생성기(`AbstractAutoProxyCreator`)와 비교. 나머지 3단계는 11~12주차(Spring AOP)에서 `ProxyFactory`/`Pointcut`/`Advisor`/`AbstractAutoProxyCreator`를 제대로 다룰 때 이어간다 — 지금 서두르면 이번 주제(BeanFactoryPostProcessor vs BeanPostProcessor의 시점 차이)의 초점이 흐려진다.
