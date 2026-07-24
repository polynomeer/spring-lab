# 빈 생성과 생명주기 — 콜백 순서와 그 순서가 정해지는 진짜 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 4주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 6(Bean Lifecycle Recorder)에 대응하는 분석 문서다.

## 1. 이번 질문

빈 하나가 생성자 호출부터 소멸까지 거치는 모든 콜백(리플렉션 생성, 의존성 주입, `Aware`, `BeanPostProcessor`, 초기화, 소멸)은 정확히 어떤 순서로 실행되는가? 그리고 그 순서는 왜 그렇게 정해졌는가 — 특히 애노테이션 기반 콜백(`@PostConstruct`)과 인터페이스 기반 콜백(`InitializingBean`), 그리고 사용자가 직접 등록한 `BeanPostProcessor`는 서로 어떤 우선순위를 갖는가?

## 2. 공식 문서 요약

- 빈 생명주기는 대략 "인스턴스화 → 프로퍼티 설정(DI) → `Aware` 콜백 → 초기화 전 후처리 → 초기화 콜백 → 초기화 후 후처리 → [사용] → 소멸 콜백" 순서로 진행된다.
- `BeanPostProcessor`는 컨테이너가 관리하는 **모든** 빈의 초기화 전후에 개입한다. `InstantiationAwareBeanPostProcessor`는 한 걸음 더 나아가 **인스턴스화 자체**의 전후에도 개입할 수 있다 (before-instantiation에서 non-null을 반환하면 일반적인 생성·초기화 파이프라인 전체를 건너뛰고 그 값을 바로 최종 빈으로 사용한다 — AOP 프록시가 실제 빈을 완전히 대체하는 메커니즘의 기반).
- `@PostConstruct`/`@PreDestroy`(JSR-250 표준), `InitializingBean`/`DisposableBean`(Spring 고유 인터페이스), `initMethod`/`destroyMethod`(설정으로 지정하는 임의 메서드) 세 가지 초기화·소멸 메커니즘이 공존할 수 있다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `Aware` 콜백(`BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`)이 하나의 통일된 단계로 한 번에 처리될 것이라 예상했다.
- `@PostConstruct`가 `InitializingBean.afterPropertiesSet()`보다 먼저 실행될 것이라 예상은 했지만(→ 확인됨), 그 이유가 "둘 다 특별한 '초기화 콜백' 카테고리에 속해서 우선순위가 있다"는 정도로만 막연히 생각했다 — 실제로는 훨씬 구조적인 이유가 있었다.
- 사용자가 직접 등록한 `BeanPostProcessor`가 Spring 내부의 애노테이션 처리 `BeanPostProcessor`(`CommonAnnotationBeanPostProcessor` 등)보다 **나중에** 실행될 것이라 예상했다 — 실제로는 정반대였다.
- 초기화 실패 시 싱글턴 캐시에서 명시적으로 "제거"하는 코드가 있을 것이라 예상했다 — 실제로는 그런 코드가 필요 없었다(6주차에서 이미 확인).

## 4. 최소 재현 코드

[`experiments/bean-lifecycle-recorder`](../../experiments/bean-lifecycle-recorder)

```java
public class LifecycleTarget implements
        BeanNameAware, BeanFactoryAware, ApplicationContextAware, InitializingBean, DisposableBean {

    public LifecycleTarget() { LifecycleEventLog.record("constructor"); }

    @Autowired
    public void inject(Dependency dependency) { LifecycleEventLog.record("@Autowired"); }

    @PostConstruct
    public void postConstruct() { LifecycleEventLog.record("@PostConstruct"); }

    @Override
    public void afterPropertiesSet() { LifecycleEventLog.record("afterPropertiesSet"); }

    public void customInit() { LifecycleEventLog.record("customInitMethod"); }

    @PreDestroy
    public void preDestroy() { LifecycleEventLog.record("@PreDestroy"); }

    @Override
    public void destroy() { LifecycleEventLog.record("destroy"); }

    public void customDestroy() { LifecycleEventLog.record("customDestroyMethod"); }
}
```

`@Component` 스캔으로는 `initMethod`/`destroyMethod`를 지정할 수 없어서, `@Bean(initMethod = "customInit", destroyMethod = "customDestroy")`로 등록했다 — [`LifecycleConfig.java`](../../experiments/bean-lifecycle-recorder/src/main/java/lab/experiments/lifecycle/LifecycleConfig.java). 전체 코드: [`BeanLifecycleRecorderLab.java`](../../experiments/bean-lifecycle-recorder/src/main/java/lab/experiments/lifecycle/BeanLifecycleRecorderLab.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `InstantiationAwareBeanPostProcessor` | 인스턴스화 자체의 전후에 개입. before-instantiation이 non-null을 반환하면 나머지 파이프라인을 건너뜀 |
| `BeanPostProcessor` | 초기화 전후에 개입. `ApplicationContextAwareProcessor`, `CommonAnnotationBeanPostProcessor` 등 Spring 내부 기능 대부분이 이것으로 구현됨 |
| `PriorityOrdered` / `Ordered` | `BeanPostProcessor`/`BeanFactoryPostProcessor` 실행 순서를 정하는 우선순위 계약 |
| `MergedBeanDefinitionPostProcessor` | `BeanPostProcessor` 중, 병합된 `BeanDefinition` 메타데이터가 필요해서 **항상 맨 마지막에 재등록**되는 특수 부분집합 |
| `InitDestroyAnnotationBeanPostProcessor` | `@PostConstruct`/`@PreDestroy` 처리의 실제 구현체 (`CommonAnnotationBeanPostProcessor`의 부모 클래스) |
| `ApplicationContextAwareProcessor` | `ApplicationContextAware` 등 "컨테이너 레벨" `Aware` 인터페이스를 처리하는 평범한 `BeanPostProcessor` |
| `InitializingBean` / `DisposableBean` | Spring 고유의 초기화·소멸 콜백 계약 |
| `DisposableBeanAdapter` | 소멸 콜백 3종(`@PreDestroy`, `DisposableBean`, custom destroy method)을 하나의 순서로 조율 |
| `SmartInitializingSingleton` | 모든 singleton의 사전 생성이 끝난 뒤 한 번 호출되는 콜백 |

## 6. 호출 흐름

시퀀스 다이어그램: [`diagrams/lifecycle-sequence.md`](diagrams/lifecycle-sequence.md)

```text
createBeanInstance
  → InstantiationAwareBPP#postProcessBeforeInstantiation  (non-null이면 여기서 끝)
  → 생성자 호출
  → InstantiationAwareBPP#postProcessAfterInstantiation
populateBean
  → @Autowired 세터 호출 (AutowiredAnnotationBeanPostProcessor)
initializeBean
  → invokeAwareMethods()                     BeanNameAware, BeanClassLoaderAware, BeanFactoryAware (하드코딩)
  → applyBeanPostProcessorsBeforeInitialization()
      1. PriorityOrdered BPP (ApplicationContextAwareProcessor 등 prepareBeanFactory에서 제일 먼저 등록된 것들)
      2. Ordered BPP
      3. 순서 없는 일반 BPP  ← 우리가 @Component로 등록한 커스텀 BPP는 여기
      4. MergedBeanDefinitionPostProcessor 재등록 (맨 끝)  ← CommonAnnotationBeanPostProcessor(@PostConstruct)는 여기
  → invokeInitMethods()
      - InitializingBean#afterPropertiesSet()
      - custom init method (@Bean(initMethod=...))
  → applyBeanPostProcessorsAfterInitialization()   (역시 1~4 순서, 각자의 postProcessAfterInitialization)
finishBeanFactoryInitialization 마지막
  → SmartInitializingSingleton#afterSingletonsInstantiated()
finishRefresh
  → ContextRefreshedEvent 발행

--- context.close() ---
DisposableBeanAdapter#destroy()
  → DestructionAwareBeanPostProcessor#postProcessBeforeDestruction()   (@PreDestroy)
  → DisposableBean#destroy()
  → custom destroy method (@Bean(destroyMethod=...))
```

## 7. 브레이크포인트

이번 주제는 로그 기반 관찰(8번)과 소스 코드 확인(9번)으로 검증했고, `tools/jdi-tracer`로 직접 추적하지는 않았다. 다음은 추적 후보로 남겨둔다.

```text
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBeanInstance
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#initializeBean
org.springframework.context.support.PostProcessorRegistrationDelegate#registerBeanPostProcessors
org.springframework.beans.factory.support.DisposableBeanAdapter#destroy
```

## 8. 런타임 관찰

`BeanLifecycleRecorderLab`을 실행해서 얻은 실제 순서 ([`BeanLifecycleOrderTest`](../../experiments/bean-lifecycle-recorder/src/test/java/lab/experiments/lifecycle/BeanLifecycleOrderTest.java)로 고정):

```text
InstantiationAwareBPP:beforeInstantiation
constructor
InstantiationAwareBPP:afterInstantiation
@Autowired
BeanNameAware
BeanFactoryAware
ApplicationContextAware
BPP:beforeInitialization        ← 우리 커스텀 BeanPostProcessor
@PostConstruct                  ← CommonAnnotationBeanPostProcessor
afterPropertiesSet
customInitMethod
BPP:afterInitialization         ← 우리 커스텀 BeanPostProcessor
SmartInitializingSingleton:afterSingletonsInstantiated
event:ContextRefreshedEvent
--- close() ---
@PreDestroy
destroy
customDestroyMethod
```

가장 예상 밖이었던 지점: **우리가 직접 등록한 평범한 `BeanPostProcessor`의 `postProcessBeforeInitialization`이 `@PostConstruct`보다 먼저 실행됐다.** `@PostConstruct`를 처리하는 `CommonAnnotationBeanPostProcessor`가 Spring 내부 확장 지점이니 당연히 먼저 실행될 거라 예상했는데 정반대였다 — 이유는 9번·11번에서 소스로 확인했다.

`BeanCreationFailureCacheTest`는 6주차에서 예고한 질문("초기화 콜백에서 예외가 나면 캐시는?")을 이번 주 소스로 재확인했다: `afterPropertiesSet()`에서 예외를 던지는 빈은 `refresh()`를 `BeanCreationException`으로 실패시키고, 그 이름은 `beanFactory.containsSingleton(name)`에서 끝까지 `false`다.

## 9. 공식 테스트 분석 / 소스 확인

`spring-framework` v6.2.19 소스(`/Users/hammac/Study/spring-framework-src`)로 확인했다. 이번 주제의 핵심 발견은 단일 테스트 메서드 하나가 아니라 **등록 알고리즘 자체**에 있어서, 관련 테스트보다 소스를 직접 인용한다.

**`@PostConstruct`가 우리 커스텀 BPP보다 늦게 실행된 이유** — `PostProcessorRegistrationDelegate.registerBeanPostProcessors()` (`spring-context`)
```java
// Separate between BeanPostProcessors that implement PriorityOrdered, Ordered, and the rest.
for (String ppName : postProcessorNames) {
    if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
        BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
        priorityOrderedPostProcessors.add(pp);
        if (pp instanceof MergedBeanDefinitionPostProcessor) {
            internalPostProcessors.add(pp);          // ← 여기 다시 담긴다
        }
    }
    ...
}
// 1. PriorityOrdered 등록 → 2. Ordered 등록 → 3. 순서 없는 일반 BPP 등록(우리 커스텀 BPP가 여기)
// 4. 마지막으로 internalPostProcessors를 "다시" 등록
sortPostProcessors(internalPostProcessors, beanFactory);
registerBeanPostProcessors(beanFactory, internalPostProcessors);
```
`CommonAnnotationBeanPostProcessor`의 부모 `InitDestroyAnnotationBeanPostProcessor`는 `PriorityOrdered`를 구현하면서 **동시에** `MergedBeanDefinitionPostProcessor`도 구현한다. 그래서 1단계(PriorityOrdered)에서 한 번 등록됐다가, `internalPostProcessors`에도 담겨서 **4단계에서 다시 등록**된다. 그리고 실제 목록에 반영하는 `AbstractBeanFactory.addBeanPostProcessor()`는:
```java
public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
    synchronized (this.beanPostProcessors) {
        this.beanPostProcessors.remove(beanPostProcessor);  // 기존 위치에서 제거
        this.beanPostProcessors.add(beanPostProcessor);     // 맨 끝에 추가
    }
}
```
"기존 위치에서 제거하고 맨 끝에 다시 추가"하기 때문에, `PriorityOrdered`로 가장 먼저 등록됐던 `CommonAnnotationBeanPostProcessor`가 결국 **가장 나중 위치**로 밀려난다. 우리가 `@Component`로 등록한 순서 없는(non-Ordered) 커스텀 `BeanPostProcessor`는 3단계에서 한 번만 등록되고 다시 옮겨지지 않으므로, 4단계에서 재배치되는 `CommonAnnotationBeanPostProcessor`보다 앞에 남는다 — 우리가 관찰한 순서와 정확히 일치한다.

**`ApplicationContextAware`가 별도의 "Aware 단계"가 아니라 그냥 BeanPostProcessor인 이유** — `AbstractApplicationContext.prepareBeanFactory()`
```java
beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
```
`ApplicationContextAwareProcessor implements BeanPostProcessor`이고 `refresh()`의 3번째 단계(`prepareBeanFactory`)에서 등록된다 — 우리 커스텀 BPP(5~6단계에서 등록)보다 먼저 목록에 들어가므로, `postProcessBeforeInitialization`이 호출될 때 항상 우리 BPP보다 먼저 실행된다. 반면 `BeanNameAware`/`BeanClassLoaderAware`/`BeanFactoryAware` 세 가지만 `AbstractAutowireCapableBeanFactory.invokeAwareMethods()`에 하드코딩되어 있어, `BeanPostProcessor` 목록과 무관하게 항상 가장 먼저 실행된다.

**소멸 콜백 순서** — `DisposableBeanAdapter.destroy()`
```java
for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
    processor.postProcessBeforeDestruction(this.bean, this.beanName);   // @PreDestroy
}
if (this.invokeDisposableBean) {
    ((DisposableBean) this.bean).destroy();                            // destroy()
}
// (뒤이어 custom destroy method 호출)
```
우리가 관찰한 `@PreDestroy → destroy → customDestroyMethod` 순서가 이 메서드 하나에 그대로 담겨 있다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

이번 주제에 대응하는 축소 구현(project 7, Mini Bean Lifecycle Pipeline)은 아직 만들지 않았다. `mini-spring/mini-container`의 `SimpleBeanFactory`는 리플렉션으로 기본 생성자만 호출할 뿐, `BeanPostProcessor` 체인이나 초기화·소멸 콜백이 전혀 없다.

**다음 project 7에서 다룰 격차**
- `BeanPostProcessor` 체인 자체(전/후 인터페이스, 등록 순서)
- `InitializingBean`/커스텀 init 메서드 호출
- 이번 주 가장 흥미로웠던 지점인 "우선순위 있는 후처리기가 특수 조건(`MergedBeanDefinitionPostProcessor`)에서 맨 끝으로 밀려나는" 재등록 메커니즘까지 재현할지는 project 7에서 스코프를 정할 문제 — 처음에는 단순히 등록 순서대로만 실행하는 것으로 시작하는 편이 나을 것이다.

## 11. Spring 설계 의도

- **`InstantiationAwareBeanPostProcessor`가 생성자 호출 자체를 가로챌 수 있게 한 이유**: AOP 프록시나 특수한 대리 객체가 필요한 빈은 원본 클래스를 리플렉션으로 생성하는 대신 완전히 다른 객체를 반환해야 한다. before-instantiation 단계를 생성자 호출보다 앞에 두고 non-null 반환 시 나머지 파이프라인 전체를 생략하게 만들면, "이 빈은 다른 방식으로 만들어진다"는 특수 케이스를 `AbstractAutowireCapableBeanFactory`가 몰라도 되게 만들 수 있다.
- **`Aware` 인터페이스를 두 그룹(하드코딩 vs BeanPostProcessor)으로 나눈 이유**: `BeanNameAware`/`BeanClassLoaderAware`/`BeanFactoryAware`는 `BeanFactory` 계층 자체의 개념이라 `AbstractAutowireCapableBeanFactory`가 직접 알아도 무방하다. 반면 `ApplicationContextAware`/`EnvironmentAware`/`ApplicationEventPublisherAware` 등은 `BeanFactory`가 아니라 `ApplicationContext`(더 상위 계층)의 개념이므로, `BeanFactory` 구현체가 이들을 직접 알 필요가 없도록 `ApplicationContextAwareProcessor`라는 평범한 `BeanPostProcessor`로 분리해 두었다 — 계층 분리(`BeanFactory` vs `ApplicationContext`)를 확장 지점 설계에도 그대로 반영한 것이다.
- **`MergedBeanDefinitionPostProcessor`를 맨 끝으로 재등록하는 이유**: 이 부류의 후처리기는 병합된 `BeanDefinition`의 최종 메타데이터(예: `@PostConstruct`가 붙은 메서드 목록)를 필요로 한다. 다른 모든 `BeanPostProcessor`(사용자 정의 포함)가 등록을 마친 뒤에 실행되도록 강제하면, "어떤 애노테이션 처리기가 사용자 코드보다 먼저 끼어들어 예상치 못한 부작용을 일으키는" 상황을 줄일 수 있다 — 자체 상태(`PriorityOrdered`)보다 이 구조적 제약(맨 끝 재등록)이 우선한다.

## 12. 결론 (예상과 실제의 차이)

- 예상대로였던 것: `@PostConstruct`가 `afterPropertiesSet()`보다 먼저, `afterPropertiesSet()`이 custom init method보다 먼저, `@PreDestroy`가 `destroy()`보다 먼저.
- 예상과 크게 달랐던 것: 우리가 직접 등록한 평범한 `BeanPostProcessor`가 Spring 내부의 `@PostConstruct` 처리기보다 **먼저** 실행됐다 — `PriorityOrdered`라는 우선순위 표시가 있어도, `MergedBeanDefinitionPostProcessor`라는 조건에 걸리면 등록 알고리즘이 그 우선순위를 무시하고 맨 끝으로 재배치한다.
- 예상과 달랐던 것: `ApplicationContextAware`는 별도의 "Aware 단계"가 아니라 그냥 하나의 `BeanPostProcessor`(`ApplicationContextAwareProcessor`)였다 — 다만 매우 일찍(`prepareBeanFactory()`) 등록되기 때문에 다른 `BeanPostProcessor`보다 항상 먼저 실행되는 것처럼 "보였을" 뿐이다.
- 새로 열린 질문: 이 재등록 메커니즘을 `tools/jdi-tracer`로 실제 추적해 보지는 않았다(7번). Project 7(Mini Bean Lifecycle Pipeline)에서 이 복잡도를 얼마나 재현할지는 별도로 정할 문제다.
