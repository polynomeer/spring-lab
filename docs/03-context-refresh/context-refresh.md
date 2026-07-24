# ApplicationContext.refresh() — 확장 지점의 실행 순서와 컨텍스트 생명주기

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 3주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 5(Context Refresh Visualizer)에 대응하는 분석 문서다.

## 1. 이번 질문

`ApplicationContext.refresh()` 동안 `BeanFactoryPostProcessor`, `BeanPostProcessor`, non-lazy singleton 생성, `ContextRefreshedEvent`는 정확히 어떤 순서로 실행되는가? 그리고 `refresh()` 전후·`close()` 이후·부모/자식 컨텍스트 사이에서 컨테이너는 어떻게 다르게 동작하는가?

## 2. 공식 문서 요약

- `refresh()`는 Spring 컨테이너 초기화의 중심 메서드다. `BeanDefinition` 등록이 끝난 뒤 이 메서드 하나가 BeanFactoryPostProcessor 실행, BeanPostProcessor 등록, non-lazy singleton 생성, 이벤트 발행까지 전부를 정해진 순서로 조율한다.
- 표준 12단계: `prepareRefresh → obtainFreshBeanFactory → prepareBeanFactory → postProcessBeanFactory → invokeBeanFactoryPostProcessors → registerBeanPostProcessors → initMessageSource → initApplicationEventMulticaster → onRefresh → registerListeners → finishBeanFactoryInitialization → finishRefresh`.
- `ApplicationContext`는 `BeanFactory` 위에 이벤트 발행(`ApplicationEventPublisher`), 국제화, 부모-자식 계층 구조 같은 애플리케이션 인프라를 추가로 제공하며, `refresh()`는 그 인프라들을 실제로 조립하는 단계다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `BeanFactoryPostProcessor`가 `BeanPostProcessor` 등록보다 먼저 실행될 것이라 예상했다 (→ 확인됨).
- lazy 빈과 prototype 빈은 `refresh()` 동안 생성되지 않을 것이라 예상했다 (→ 확인됨).
- `ContextRefreshedEvent`는 모든 non-lazy singleton이 만들어진 뒤에 발행될 것이라 예상했다 (→ 확인됨).
- `refresh()`를 두 번 호출하면 (XML 기반 컨텍스트처럼) 컨테이너가 재구성될 것이라 예상했다 — 실제로는 `AnnotationConfigApplicationContext`가 이를 막았다 (→ 예상과 다름).
- 초기화 중 예외가 나면 "초기화 실패"를 명시하는 에러가 날 것이라 예상했다 — 실제로는 "아직 refresh되지 않았다"는, 실패 사실을 직접 언급하지 않는 메시지였다 (→ 예상과 다름).
- 자식 컨텍스트에서 발행한 이벤트가 부모의 리스너에까지 전파될 것이라고는 예상하지 못했다 (→ 예상 밖의 발견).

## 4. 최소 재현 코드

[`experiments/context-refresh-visualizer`](../../experiments/context-refresh-visualizer)

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
context.register(
        EagerSingleton.class,
        LazySingleton.class,
        PrototypeBean.class,
        LoggingBeanFactoryPostProcessor.class,
        LoggingBeanPostProcessor.class,
        LoggingApplicationListener.class
);
context.refresh();
```

각 클래스의 생성자·후처리 메서드·리스너가 공유 로그(`RefreshEventLog`)에 자기 이름을 기록한다. 전체 코드: [`ContextRefreshVisualizerLab.java`](../../experiments/context-refresh-visualizer/src/main/java/lab/experiments/refresh/ContextRefreshVisualizerLab.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AbstractApplicationContext` | `refresh()`의 12단계 흐름 제어. `active`/`closed` 플래그로 생명주기 상태 관리 |
| `GenericApplicationContext` | `refresh()`를 단 한 번만 허용하도록 `AtomicBoolean` 가드 추가 (XML 기반 `AbstractRefreshableApplicationContext`와의 핵심 차이) |
| `AnnotationConfigApplicationContext` | `GenericApplicationContext` + 애노테이션 기반 빈 등록/스캔 |
| `BeanFactoryPostProcessor` | `BeanDefinition`이 모두 등록된 뒤, 빈이 하나도 생성되기 전에 개입하는 확장 지점 |
| `BeanPostProcessor` | 개별 빈의 초기화 전후에 개입하는 확장 지점 |
| `ApplicationListener<ContextRefreshedEvent>` | 컨테이너 초기화 완료 시점에 알림을 받는 확장 지점 |
| `ConfigurableListableBeanFactory` | `refresh()`가 실제로 조작하는 대상 (`preInstantiateSingletons()` 등) |

## 6. 호출 흐름

12단계와 우리가 관찰한 이벤트의 대응: [`diagrams/refresh-phases.md`](diagrams/refresh-phases.md)

```text
prepareRefresh → obtainFreshBeanFactory → prepareBeanFactory → postProcessBeanFactory
  → invokeBeanFactoryPostProcessors        [BFPP:invoked]
  → registerBeanPostProcessors
  → initMessageSource → initApplicationEventMulticaster → onRefresh → registerListeners
  → finishBeanFactoryInitialization        [constructor:eagerSingleton, BPP:before/after:eagerSingleton]
       (lazySingleton / prototypeBean 은 이 단계에서 건드리지 않음)
  → finishRefresh                          [event:ContextRefreshedEvent]
```

## 7. 브레이크포인트

이번 주제는 `tools/jdi-tracer`로 직접 추적하지 않고, 로그 기반 관찰(8번)과 소스 코드 확인(9번)으로만 검증했다. 다음은 다음 세션의 추적 후보로 남겨둔다.

```text
org.springframework.context.support.AbstractApplicationContext#refresh
org.springframework.context.support.AbstractApplicationContext#invokeBeanFactoryPostProcessors
org.springframework.context.support.AbstractApplicationContext#finishBeanFactoryInitialization
org.springframework.context.support.AbstractApplicationContext#finishRefresh
org.springframework.context.support.AbstractApplicationContext#publishEvent
```

## 8. 런타임 관찰

`ContextRefreshOrderingTest`가 실제로 기록한 순서 ([`ContextRefreshVisualizerLab`](../../experiments/context-refresh-visualizer/src/main/java/lab/experiments/refresh/ContextRefreshVisualizerLab.java) 실행 결과와 동일):

```text
BFPP:invoked
constructor:eagerSingleton
BPP:before:eagerSingleton
BPP:after:eagerSingleton
event:ContextRefreshedEvent:<context display name>
```

- `lazySingleton`/`prototypeBean`의 생성자 로그는 `refresh()` 동안 **전혀 등장하지 않는다.** lazy 빈은 최초 `getBean()` 때, prototype 빈은 `getBean()`을 호출할 때마다(매번 새로) 생성된다 (`ContextRefreshOrderingTest.lazySingletonIsNotCreatedUntilFirstGetBean`, `.prototypeBeanIsCreatedOnlyOnDemandAndEveryTime`).
- **컨텍스트 생명주기** (`ContextLifecycleTest`)
  - `refresh()` 이전 `getBean()` → `IllegalStateException("... has not been refreshed yet")`
  - `close()` 이후 `getBean()` → `IllegalStateException("... has been closed already")`
  - `refresh()`를 두 번째 호출 → `IllegalStateException("... does not support multiple refresh attempts: just call 'refresh' once")`
  - 생성자가 예외를 던지는 빈으로 `refresh()`가 실패하면 → `BeanCreationException`이 던져지고, 그 이후 `getBean()`은 (닫힌 게 아니라) **"아직 refresh되지 않았다"** 메시지를 낸다 — refresh를 시도했다가 실패했는데도 메시지는 "시도조차 안 한 것"처럼 보인다.
- **부모/자식 컨텍스트** (`ParentChildContextTest`)
  - 자식은 부모의 빈을 조회할 수 있지만, 부모는 자식의 빈을 조회하면 `NoSuchBeanDefinitionException`.
  - 같은 이름의 빈이 양쪽에 있으면 각 컨텍스트는 **자기 자신의 정의를 우선** 사용한다 (부모 조회는 부모 정의를, 자식 조회는 자식 정의를 반환 — 자식이 부모 정의를 "덮어쓰는" 게 아니라 완전히 별개의 등록소를 갖는다).
  - 자식에서 발행한 이벤트는 부모의 리스너에도 전파된다. 부모 리스너를 하나만 등록해 두고 부모→자식 순서로 각각 `refresh()`하면, 그 리스너는 총 두 번(부모 자신의 `ContextRefreshedEvent` + 자식에서 전파된 `ContextRefreshedEvent`) 호출된다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스(`/Users/hammac/Study/spring-framework-src`)로 확인했다.

**단일 refresh 가드** — `spring-context/src/main/java/org/springframework/context/support/GenericApplicationContext.java`
```java
protected final void refreshBeanFactory() throws IllegalStateException {
    if (!this.refreshed.compareAndSet(false, true)) {
        throw new IllegalStateException(
                "GenericApplicationContext does not support multiple refresh attempts: just call 'refresh' once");
    }
    ...
}
```
공식 테스트 스위트에는 "평범한 `refresh()`를 두 번 호출"하는 테스트는 없었지만, 같은 가드를 사용하는 `refreshForAotFailsOnAnActiveContext()`(`GenericApplicationContextTests`)가 `refresh()`로 활성화된 컨텍스트에 대해 두 번째 초기화 시도(`refreshForAotProcessing`)가 정확히 같은 메시지로 실패하는 것을 검증하고 있었다.

**부모 컨텍스트로의 이벤트 전파** — `spring-context/src/test/java/org/springframework/context/event/PayloadApplicationEventTests.java`
```java
@Test
void eventClassWithPayloadTypeOnParentContext() {
    ConfigurableApplicationContext parent = new AnnotationConfigApplicationContext(NumberHolderListener.class);
    ConfigurableApplicationContext ac = new GenericApplicationContext(parent);
    ac.refresh();

    ac.publishEvent(event);
    assertThat(parent.getBean(NumberHolderListener.class).events).contains(event.getPayload());
    ...
}
```
자식(`ac`)에서 `publishEvent()`를 호출했는데 검증은 **부모**의 리스너 빈에서 한다 — 우리가 `ParentChildContextTest.eventsPublishedInChildPropagateToParentListeners`에서 직접 재현한 것과 정확히 같은 패턴이다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

이번 주제는 `mini-spring`에 대응하는 축소 구현을 아직 만들지 않았다. `mini-spring/mini-container`의 `SimpleBeanFactory`는 `BeanFactory` 수준(`getBean`/`registerBeanDefinition`)까지만 다루고, `refresh()`에 해당하는 "여러 확장 지점을 정해진 순서로 조율하는 상위 컨테이너" 개념 자체가 없다.

**아직 없는 것**
- `BeanFactoryPostProcessor`/`BeanPostProcessor` 확장 지점 — project 7(Mini Bean Lifecycle Pipeline)에서 다룰 대상.
- non-lazy singleton을 컨테이너 초기화 시점에 미리 만드는 절차(`preInstantiateSingletons()`에 해당하는 것) — 지금은 `getBean()`을 직접 호출하기 전에는 아무것도 만들어지지 않는다(전부 lazy인 셈).
- 이벤트 발행/구독, 부모-자식 계층 — 아직 계획에 없음.

## 11. Spring 설계 의도

- **`GenericApplicationContext`가 단일 refresh만 허용하는 이유**: XML 기반 `AbstractRefreshableApplicationContext`는 설정 파일이 바뀌면 `BeanFactory` 자체를 통째로 새로 만들고 다시 `refresh()`하는 "재로딩" 시나리오를 위해 설계됐다. 반면 `GenericApplicationContext`는 프로그래밍 방식으로 `BeanDefinition`을 자유롭게 등록한 뒤 "이제 됐다"는 의미로 `refresh()`를 한 번 호출하는 용도다. 재로딩을 지원하지 않는 대신, 한 번 활성화된 뒤에는 상태가 훨씬 단순해진다.
- **초기화 실패 메시지가 "실패했다"고 말하지 않는 이유**: `cancelRefresh()`는 `active` 플래그만 내리고 별도의 "실패함" 상태를 두지 않는다. `assertBeanFactoryActive()`는 오직 `active`/`closed` 두 플래그만으로 메시지를 결정하므로, "활성화된 적이 없음"과 "활성화하려다 실패함"이 같은 메시지로 뭉뚱그려진다 — 상태 머신을 단순하게 유지하는 대가로 진단 메시지의 정밀도를 일부 희생한 사례다.
- **자식의 이벤트가 부모에게 전파되는 이유**: 계층형 컨텍스트(예: 루트 컨텍스트 + 웹 MVC 자식 컨텍스트)에서, 상위 컨텍스트에 등록된 공통 리스너(로깅, 감사 등)가 하위 컨텍스트 각각에서 벌어지는 일까지 한곳에서 관찰할 수 있어야 하기 때문이다. 반대로 부모→자식 전파는 없는데, 자식은 여러 개일 수 있고 부모의 이벤트가 어떤 자식들과 관련 있는지 프레임워크가 알 방법이 없다 — "위로 모으는" 방향만 안전하게 일반화할 수 있다.

## 12. 결론 (예상과 실제의 차이)

- 예상대로였던 것: BFPP → BPP → non-lazy singleton 생성 → `ContextRefreshedEvent` 순서, lazy/prototype 빈이 `refresh()` 중 생성되지 않는다는 것.
- 예상과 달랐던 것: `AnnotationConfigApplicationContext`는 `refresh()`를 한 번만 허용한다 — "컨텍스트를 다시 초기화"하는 건 컨텍스트 종류(XML 기반 vs `Generic`)에 따라 아예 불가능할 수 있다.
- 예상과 달랐던 것: 초기화 실패 후의 에러 메시지가 실패 사실을 직접 언급하지 않는다 — 상태 플래그가 단순화된 대가.
- 예상 밖의 발견: 자식 컨텍스트의 이벤트가 부모의 리스너까지 전파된다(단방향).
- 새로 열린 질문: `tools/jdi-tracer`로 `refresh()`의 12단계를 실제로 브레이크포인트 찍어 보지는 않았다(7번) — 이번엔 로그와 소스 읽기로만 확인했다. 다음은 4주차(빈 생성과 생명주기, 프로젝트 6 Bean Lifecycle Recorder)로 이어져 `@PostConstruct`/`InitializingBean`/`@PreDestroy`까지 포함한 전체 콜백 순서를 다룬다.
