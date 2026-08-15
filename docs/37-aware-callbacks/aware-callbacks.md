# Aware 인터페이스 — 같은 이름의 패턴이 실제로는 두 개의 서로 다른 메커니즘이다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`36`](../36-custom-scope/custom-scope.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`35-import-selector`](../35-import-selector/import-selector.md)에서 `ImportAware`(`@Import`로 가져온 클래스가 가져온 애노테이션의 정보를 받는 콜백)를 다루면서 "`Aware` 패턴"이라는 더 큰 계열이 있다는 걸 스쳐 지나갔는데, 이번엔 그 계열 전체 - `BeanNameAware`부터 `ApplicationContextAware`까지 - 를 한 빈에 전부 구현해서, 겉보기엔 똑같아 보이는 "Aware" 접미사 뒤에 실제로는 **두 개의 완전히 다른 메커니즘**이 숨어 있다는 것을 확인한다.

## 1. 이번 질문

- `Aware` 인터페이스는 전부 같은 방식(예: 하나의 `BeanPostProcessor`)으로 처리되는가?
- 여러 `Aware` 인터페이스를 동시에 구현하면, 그 콜백들은 어떤 순서로 호출되는가?
- `BeanFactoryAware`가 받는 `BeanFactory`는 실제로 어떤 객체인가 - 컨테이너가 쓰는 바로 그 인스턴스인가, 아니면 제한된 뷰인가?
- 사용자 정의 `BeanPostProcessor` 빈 자신도 `ApplicationContextAware`를 구현하면 정상적으로 컨텍스트를 받을 수 있는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Other `Aware` Interfaces")는 `BeanNameAware`/`BeanFactoryAware`/`ApplicationContextAware` 등 다양한 `Aware` 인터페이스 목록을 나열하고 "컨테이너가 특정 인프라 의존성을 이 빈에 노출시켜 준다"고 설명한다.
- 문서는 이 인터페이스들이 "가능하면 피하라"고 권고한다(코드가 Spring API에 결합되므로) - 하지만 이 열 개 남짓한 인터페이스가 내부적으로 **하나의 메커니즘을 공유하는지**는 다루지 않는다.
- Javadoc 수준에서는 각 `Aware` 인터페이스가 개별적으로 "누가 이 값을 채워 주는지"를 설명하지만(예: `BeanFactoryAware`는 "포함하는 `BeanFactory`에 의해 채워짐"), 그 "누가"가 인터페이스마다 실제로 다른 클래스라는 것은 각 문서를 따로 읽어서는 알아채기 어렵다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 이름이 전부 `Aware`로 끝나니, 하나의 공통된 처리기(`BeanPostProcessor` 하나)가 열 개 인터페이스를 순서 없이 다 처리할 거라 예상했다 — **틀렸다.** 정확히 셋(`BeanNameAware`/`BeanClassLoaderAware`/`BeanFactoryAware`)은 `BeanPostProcessor`조차 아니고, `AbstractAutowireCapableBeanFactory`가 초기화 과정에서 **직접** 호출한다 - 나머지 일곱만 `ApplicationContextAwareProcessor`라는 `BeanPostProcessor`가 처리한다.
- 여러 `Aware`를 함께 구현하면 호출 순서가 (인터페이스 선언 순서나 알파벳 순서처럼) 불특정할 거라 예상했다 — **틀렸다.** 순서는 완전히 고정돼 있다 - 직접 호출되는 셋이 항상 먼저, `BeanPostProcessor`가 처리하는 일곱이 항상 그 뒤, 그리고 그 일곱 안에서도 코드에 나열된 순서가 그대로 실행 순서다.
- 사용자 정의 `BeanPostProcessor` 빈은 자기 자신이 `BeanPostProcessor`이기도 하므로, 다른 `BeanPostProcessor`(`ApplicationContextAwareProcessor` 포함)의 적용 대상에서 제외될 거라 예상했다(닭과 달걀 문제처럼) — **틀렸다.** `ApplicationContextAwareProcessor`는 사용자 정의 `BeanPostProcessor` 빈들보다 항상 먼저 등록돼 있으므로, 그 빈들도 정상적으로 `ApplicationContextAware` 콜백을 받는다.
- `BeanFactoryAware`가 받는 `BeanFactory`는 캡슐화를 위해 제한된 읽기 전용 뷰일 거라 예상했다 — **틀렸다.** 컨테이너가 실제로 쓰는 구체 클래스 인스턴스(`AbstractAutowireCapableBeanFactory.this`) 그 자체가 그대로 전달된다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/aware-callback-lab`](../../experiments/aware-callback-lab)

```java
public class RecordingAwareBean implements
        BeanNameAware, BeanClassLoaderAware, BeanFactoryAware,
        EnvironmentAware, EmbeddedValueResolverAware, ResourceLoaderAware,
        ApplicationEventPublisherAware, MessageSourceAware, ApplicationStartupAware,
        ApplicationContextAware {

    private final List<String> invocationOrder = new ArrayList<>();

    public void setBeanName(String name) { invocationOrder.add("BeanNameAware"); }
    public void setBeanFactory(BeanFactory bf) { invocationOrder.add("BeanFactoryAware"); }
    // ... 나머지 여덟 개도 각자 자기 이름을 기록
}
```

```java
public class CustomAwareBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private ApplicationContext applicationContext;
    public void setApplicationContext(ApplicationContext ctx) { this.applicationContext = ctx; }
    // postProcessBeforeInitialization()은 그냥 bean을 그대로 반환
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `Aware` | 아무 메서드도 없는 마커 인터페이스 - "나는 컨테이너로부터 뭔가를 주입받고 싶다"는 신호일 뿐 |
| `AbstractAutowireCapableBeanFactory#invokeAwareMethods` | `BeanNameAware`/`BeanClassLoaderAware`/`BeanFactoryAware` 세 개만 직접(코드로 하드코딩된 `instanceof` 체인) 처리 - `BeanPostProcessor`가 전혀 관여하지 않음 |
| `ApplicationContextAwareProcessor` | `EnvironmentAware`/`EmbeddedValueResolverAware`/`ResourceLoaderAware`/`ApplicationEventPublisherAware`/`MessageSourceAware`/`ApplicationStartupAware`/`ApplicationContextAware` 일곱 개를 처리하는 `BeanPostProcessor` - `AbstractApplicationContext#prepareBeanFactory()`(refresh()의 2번째 단계)에서 등록됨 |
| `AbstractAutowireCapableBeanFactory#initializeBean` | `invokeAwareMethods()`(직접 호출, 셋)를 `applyBeanPostProcessorsBeforeInitialization()`(BPP 체인, 일곱)보다 **먼저** 호출하는 지점 - 이 순서 하나가 8번 절 전체의 근거 |
| (35번에서 다룬) `ImportAware` | 이 열 개와 또 다른, **세 번째** 메커니즘(`ConfigurationClassPostProcessor`의 전용 `BeanPostProcessor`) - 이번 문서의 "Aware라는 이름이 곧 하나의 메커니즘을 뜻하지 않는다"는 결론을 한 번 더 확인시켜 줌 |

## 6. 호출 흐름

```text
AbstractAutowireCapableBeanFactory#initializeBean(beanName, bean, mbd)
  → invokeAwareMethods(beanName, bean)                          ← BeanPostProcessor 없이 직접
      if (bean instanceof BeanNameAware) bean.setBeanName(beanName)
      if (bean instanceof BeanClassLoaderAware) bean.setBeanClassLoader(getBeanClassLoader())
      if (bean instanceof BeanFactoryAware) bean.setBeanFactory(AbstractAutowireCapableBeanFactory.this)
  → applyBeanPostProcessorsBeforeInitialization(bean, beanName)  ← 등록된 BeanPostProcessor 전부 순회
      → ApplicationContextAwareProcessor#postProcessBeforeInitialization(bean, beanName)
          if (bean instanceof Aware) invokeAwareInterfaces(bean)
              if (bean instanceof EnvironmentAware) bean.setEnvironment(...)
              if (bean instanceof EmbeddedValueResolverAware) bean.setEmbeddedValueResolver(...)
              if (bean instanceof ResourceLoaderAware) bean.setResourceLoader(...)
              if (bean instanceof ApplicationEventPublisherAware) bean.setApplicationEventPublisher(...)
              if (bean instanceof MessageSourceAware) bean.setMessageSource(...)
              if (bean instanceof ApplicationStartupAware) bean.setApplicationStartup(...)
              if (bean instanceof ApplicationContextAware) bean.setApplicationContext(...)
  → invokeInitMethods(...)   (@PostConstruct, afterPropertiesSet() 등 - 4주차)
  → applyBeanPostProcessorsAfterInitialization(...)

(비교) refresh()의 등록 시점
  → prepareBeanFactory(beanFactory)           (2번째 단계) → beanFactory.addBeanPostProcessor(
                                                                new ApplicationContextAwareProcessor(this))
  → registerBeanPostProcessors(beanFactory)   (6번째 단계, 훨씬 나중) → 사용자 정의 BeanPostProcessor
                                                                  빈들이 이 시점에 만들어짐
                                                                  (이미 등록된 ApplicationContextAwareProcessor의
                                                                   적용 대상이 됨)
```

직접 호출(셋)과 `BeanPostProcessor` 위임(일곱)이 갈리는 지점, 그리고 `ApplicationContextAwareProcessor`가 사용자 정의 `BeanPostProcessor` 빈보다 먼저 등록되는 타이밍을 함께 그린 다이어그램: [`diagrams/aware-invocation-order.md`](diagrams/aware-invocation-order.md)

## 7. 브레이크포인트

25~36번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "호출 순서"라는 결과였고, `invocationOrder` 리스트로 직접 기록하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#initializeBean
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#invokeAwareMethods
org.springframework.context.support.ApplicationContextAwareProcessor#postProcessBeforeInitialization
org.springframework.context.support.ApplicationContextAwareProcessor#invokeAwareInterfaces
org.springframework.context.support.AbstractApplicationContext#prepareBeanFactory (ApplicationContextAwareProcessor 등록 지점)
```

## 8. 런타임 관찰

[`AwareCallbackTest`](../../experiments/aware-callback-lab/src/test/java/lab/experiments/aware/AwareCallbackTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| 열 개 `Aware` 인터페이스를 모두 구현한 빈의 실제 호출 순서 | `BeanNameAware → BeanClassLoaderAware → BeanFactoryAware → EnvironmentAware → EmbeddedValueResolverAware → ResourceLoaderAware → ApplicationEventPublisherAware → MessageSourceAware → ApplicationStartupAware → ApplicationContextAware` - 정확히 이 순서 하나로 고정 |
| `BeanFactoryAware`가 받은 `BeanFactory` | `context.getBeanFactory()`와 **동일한 인스턴스**(`isSameAs`) |
| `ApplicationContextAware`가 받은 컨텍스트 | 컨텍스트 자기 자신과 동일한 인스턴스 |
| 사용자 정의 `BeanPostProcessor` 빈(`CustomAwareBeanPostProcessor`)이 `ApplicationContextAware`도 구현 | 정상적으로 컨텍스트를 받음 - 자기 자신이 `BeanPostProcessor`라는 사실이 방해되지 않음 |

**직접 겪은 것**: 네 번째 실험을 설계하면서 "`BeanPostProcessor` 빈은 다른 `BeanPostProcessor`의 적용을 못 받는 것 아닌가"라는 이 저장소 5·6주차에서 이미 배운 원칙과 충돌하는 것처럼 느껴져서 잠깐 멈칫했다 - 하지만 그 원칙은 "사용자 정의 `BeanPostProcessor`끼리는 서로의 적용 대상이 아니다(등록 순서에 따라 놓칠 수 있다)"는 것이지, `ApplicationContextAwareProcessor`처럼 **컨테이너 자신이 아주 이른 단계에 미리 등록해 두는** 특수한 `BeanPostProcessor`에는 해당하지 않는다. `prepareBeanFactory()`(2번째 단계)가 `registerBeanPostProcessors()`(6번째 단계, 사용자 정의 BPP들이 만들어지는 시점)보다 먼저라는 걸 3주차 문서에서 이미 알고 있었는데도, 그 타이밍 차이가 "Aware" 문제에서도 똑같이 작동한다는 걸 이번에 실행으로 다시 연결했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다. 이번 주제도 두 클래스의 소스 자체가 가장 직접적인 근거였다.

- `AbstractAutowireCapableBeanFactory#invokeAwareMethods`의 실제 소스: `if (bean instanceof BeanNameAware ...)`, `BeanClassLoaderAware`, `BeanFactoryAware` 딱 세 개의 `instanceof` 체크만 있다는 것을 확인했다 - 다른 일곱 개는 이 메서드가 아예 모른다.
- `ApplicationContextAwareProcessor#invokeAwareInterfaces`의 실제 소스: `EnvironmentAware` → `EmbeddedValueResolverAware` → `ResourceLoaderAware` → `ApplicationEventPublisherAware` → `MessageSourceAware` → `ApplicationStartupAware` → `ApplicationContextAware` 순서로 나열된 `if` 블록들을 그대로 확인했다 - 8번 절의 뒷부분 일곱 순서가 우연이 아니라 이 코드 나열 순서 그 자체라는 근거다.
- `AbstractAutowireCapableBeanFactory#initializeBean`의 실제 소스: `invokeAwareMethods(beanName, bean);`이 `applyBeanPostProcessorsBeforeInitialization(...)` 호출보다 코드상 먼저 온다는 것을 확인했다 - 8번 절 전체 순서(직접 호출 셋이 항상 먼저)의 유일하고 정확한 근거다.
- `AbstractApplicationContext#prepareBeanFactory`의 실제 소스(1주차부터 이미 여러 번 읽은 메서드): `beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));`가 `refresh()`의 2번째 단계에서 실행된다는 것을 재확인했다 - 사용자 정의 `BeanPostProcessor` 빈들이 만들어지는 `registerBeanPostProcessors()`(6번째 단계)보다 한참 앞선다는 것이, 네 번째 실험 결과의 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-container`가 이미 `BeanPostProcessor` 체인(4·7주차)을 다뤘고, 이번 주의 가치는 새로운 골격이 아니라 "이름이 같은 패턴(`XxxAware`)이 실제로는 서로 다른 두 개(직접 호출 vs `BeanPostProcessor` 위임, 그리고 35번에서 본 `ImportAware`까지 합치면 세 개)의 메커니즘으로 나뉘어 있다"는 **분류 그 자체**에 있었다 - mini 구현보다는 열 개 인터페이스를 한 빈에 몰아넣고 실제 순서를 직접 기록하는 쪽이 이 분류를 훨씬 명확하게 보여줬다.

## 11. Spring 설계 의도

- **왜 셋(`BeanNameAware`/`BeanClassLoaderAware`/`BeanFactoryAware`)만 `BeanPostProcessor` 없이 직접 처리하는가**: 이 셋은 `ApplicationContext`가 아니라 **평범한 `BeanFactory`** 수준에서도 의미가 있는 정보다 - `BeanFactory`를 직접 쓰는(스프링의 가장 저수준 사용법) 코드에서도 자신의 이름이나 자신을 담고 있는 팩토리를 알아야 할 수 있다. 만약 이 셋마저 `ApplicationContextAwareProcessor`(이름 그대로 `ApplicationContext`에 속한 개념)에 의존한다면, `ApplicationContext` 없이 `BeanFactory`만 쓰는 저수준 시나리오에서는 이 기본적인 정보조차 얻을 수 없게 된다 - 그래서 `AbstractAutowireCapableBeanFactory` 자신이 `BeanPostProcessor`라는 더 상위 개념에 의존하지 않고 이 셋을 직접 책임진다.
- **왜 나머지 일곱은 `BeanPostProcessor`로 처리하는가**: `Environment`, `MessageSource`, `ApplicationEventPublisher` 같은 것들은 전부 `ApplicationContext`가 제공하는 상위 기능이다 - `BeanFactory` 수준에는 이런 개념 자체가 없다. 이런 컨텍스트 전용 정보를 `AbstractAutowireCapableBeanFactory`(순수 `BeanFactory` 계층)에 직접 하드코딩하면, `BeanFactory`와 `ApplicationContext`(1주차의 핵심 구분)라는 계층 분리가 깨진다 - 대신 `ApplicationContext` 구현체가 `BeanPostProcessor`(이미 존재하는 확장점)를 하나 추가로 등록하는 방식으로, `BeanFactory` 코드베이스를 전혀 건드리지 않고 이 기능을 얹었다. `BeanPostProcessor`라는 확장점 자체가 정확히 이런 "상위 계층이 하위 계층을 오염시키지 않고 기능을 추가하는" 용도로 쓰인 사례다.
- **왜 이 순서(직접 호출 → BPP 위임)가 고정돼 있어야 하는가**: `ApplicationContextAwareProcessor`가 넘겨주는 정보(예: `EnvironmentAware`) 중 일부는, 그 빈이 이미 자신의 이름이나 소속 팩토리를 알고 있다는 전제 위에서 더 유용해질 수 있다(예: 로깅 시 빈 이름을 함께 남기는 것). 더 근본적인 정보(내가 누구인지, 어느 팩토리 소속인지)가 먼저 채워지고, 그 위에 더 풍부한 컨텍스트 정보가 얹히는 순서는 "기반이 먼저, 그 위의 것이 나중"이라는 자연스러운 의존 방향을 그대로 반영한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `Aware`라는 하나의 이름 패턴이 실제로는 최소 세 개(직접 호출, `ApplicationContextAwareProcessor`, 그리고 35번에서 다룬 `ImportAware`용 별도 `BeanPostProcessor`)의 서로 다른 메커니즘으로 나뉘어 있다는 것 - "이름이 같으면 처리 방식도 같을 것"이라는 가정이 이 저장소에서 이렇게 명확하게 깨진 것은 처음이었다.
- 예상 밖이었던 것: `BeanFactoryAware`/`ApplicationContextAware`가 제한된 뷰가 아니라 컨테이너가 실제로 쓰는 구체 인스턴스를 그대로 넘겨준다는 것 - 캡슐화를 중시하는 프레임워크치고는 의외로 "날것 그대로"를 넘겨준다. 다만 이건 이 두 인터페이스를 구현하는 것 자체가 "나는 Spring 내부에 접근하겠다"는 명시적 선택이라는 걸 감안하면 자연스러운 선택이기도 하다.
- 예상대로였던 것(재확인): `BeanPostProcessor` 등록 순서가 실제 동작에 그대로 영향을 준다는 것 - 5·6주차에서 이미 배운 원칙이, 이번엔 "컨테이너가 미리 등록해 둔 특수한 BPP는 사용자 정의 BPP보다 항상 먼저"라는 구체적인 사례로 재확인됐다.
- 새로 배운 것: `BeanFactory`와 `ApplicationContext`라는 1주차의 가장 기초적인 구분이, 20여 주 뒤의 이 문서에서도 여전히 설계를 결정하는 기준이었다는 것 - 어떤 `Aware` 정보를 어느 계층이 책임지는가는, 그 정보가 `BeanFactory` 수준에서도 의미가 있는가 아니면 `ApplicationContext`가 있어야만 의미가 있는가로 정확히 갈렸다.
