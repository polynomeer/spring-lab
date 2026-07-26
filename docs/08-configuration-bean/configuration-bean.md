# @Configuration과 @Bean — 메서드 호출을 가로채서 싱글턴을 지키는 방법

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 8주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 12(Configuration Proxy Experiment)·프로젝트 13(Mini Java Config Parser)에 대응하는 분석 문서다.

## 1. 이번 질문

`proxyBeanMethods`는 정확히 무엇을 하는가? `@Bean` 메서드 안에서 다른 `@Bean` 메서드를 직접 호출했을 때 어떻게 싱글턴이 보장되는가? "누가 `@Configuration`을 분석하는가"·"`@Bean` 메서드가 어떻게 `BeanDefinition`이 되는가" 같은 나머지 질문은 2주차에서 이미 답했으므로(2번 참고), 이 문서는 **CGLIB 프록시가 메서드 호출을 가로채는 정확한 메커니즘**에 집중한다.

## 2. 공식 문서 요약 (+ 2주차에서 이미 답한 것)

- `ConfigurationClassPostProcessor`가 `@Configuration`/`@Component` 클래스를 분석해서 `@ComponentScan`·`@Bean`을 `BeanDefinition`으로 변환한다 — 2주차([`02-bean-definition/bean-definition-registration.md`](../02-bean-definition/bean-definition-registration.md))에서 `ConfigurationClassBeanDefinitionReader`까지 소스로 확인했다.
- static `@Bean`은 `beanClass` + `factoryMethodName`으로, instance `@Bean`은 `factoryBeanName` + `factoryMethodName`으로 표현되며 static은 CGLIB 강화 대상에서 아예 제외된다는 것도 2주차에서 확인했다.
- 이번 주 새로 다루는 것: `proxyBeanMethods=true`(Full, 기본값)일 때 CGLIB이 만드는 서브클래스가 **메서드 호출 자체**를 어떻게 가로채는가.

## 3. 예상 동작 (소스를 보기 전에 작성)

- Full 모드에서 메서드 직접 호출이 "어떻게든" 프록시를 거쳐 싱글턴을 반환할 것이라 예상했다 — 2주차에서 CGLIB 서브클래스가 `BeanDefinition.beanClass` 자체를 대체한다는 것까지는 확인했지만, 그 서브클래스가 **메서드 호출을 매번 가로채서 조건부로 실제 코드를 실행할지 컨테이너에 위임할지 분기한다**는 구체적 메커니즘은 예상하지 못했다.
- static `@Bean`끼리의 직접 호출은 Full 모드에서도 프록시되지 않을 것이라 예상했다(2주차 근거) — 이번엔 객체 동일성으로 직접 재확인했고 예상대로였다.
- Mini Java Config Parser를 만들기 전에는 "CGLIB 없이 어떻게 싱글턴을 흉내낼 수 있을까" 궁금했는데, 막상 만들어 보니 **흉내낼 수 없다는 것 자체가 결과물**이었다 — mini-container의 기존 `getBean()` 캐싱을 재사용하면 "파라미터로 선언한 의존성"은 싱글턴을 공유하지만, "메서드를 직접 호출"하는 경로는 애초에 컨테이너를 거치지 않으므로 원천적으로 흉내낼 방법이 없다. 이 한계 자체가 Full mode가 왜 CGLIB이라는 무거운 도구까지 동원하는지를 거꾸로 설명해준다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/configuration-proxy-lab`](../../experiments/configuration-proxy-lab)
```java
@Configuration                                    // proxyBeanMethods 기본값 true
public class FullConfiguration {
    @Bean
    public PaymentService paymentService() { return new PaymentService(); }

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentService());   // 메서드 직접 호출
    }
}
```
```java
PaymentService containerBean = context.getBean("paymentService", PaymentService.class);
OrderService orderService = context.getBean(OrderService.class);
assertThat(orderService.getPaymentService()).isSameAs(containerBean);   // Full: 통과, Lite/Component: 실패
```

**축소 구현** — [`mini-spring/mini-java-config`](../../mini-spring/mini-java-config)
```java
@MiniConfiguration
public class DemoConfig {
    @MiniBean
    PaymentService paymentService() { return new PaymentService(); }

    @MiniBean
    OrderService orderService(PaymentService paymentService) {   // 파라미터로 선언 - 싱글턴 공유됨
        return new OrderService(paymentService);
    }

    @MiniBean
    OrderService orderServiceViaDirectCall() {
        return new OrderService(paymentService());   // 직접 호출 - 항상 새 인스턴스
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ConfigurationClassEnhancer` | `@Configuration` 클래스의 CGLIB 서브클래스를 생성 (2주차에서 이미 다룸) |
| `BeanMethodInterceptor` | `ConfigurationClassEnhancer`의 내부 클래스. `@Bean` 메서드 호출마다 개입하는 CGLIB `MethodInterceptor` |
| `SimpleInstantiationStrategy` | `ThreadLocal<Method> currentlyInvokedFactoryMethod`로 "지금 컨테이너가 이 메서드를 직접 부르는 중인가"를 기록 |
| (mini) `MiniConfigurationProcessor` | CGLIB 없이 `@MiniBean` 메서드를 factory-method `BeanDefinition`으로만 등록 |
| (mini) `SimpleBeanFactory` (7주차 전 확장) | factory-method 기반 생성 + 파라미터를 `getBean(Class)`로 해석 — project 13이 의존하는 기존 파이프라인 |

## 6. 호출 흐름

시퀀스 다이어그램: [`diagrams/bean-method-interception.md`](diagrams/bean-method-interception.md)

```text
BeanMethodInterceptor#intercept(configInstance, method, args, cglibProxy)
  → isCurrentlyInvokedFactoryMethod(method)?
      true  (컨테이너 자신이 지금 이 빈을 만들려고 부른 것)
        → cglibMethodProxy.invokeSuper(...)   실제 메서드 바디 실행 (진짜 생성)
      false (사용자 코드 또는 다른 @Bean 메서드가 크로스 레퍼런스로 부른 것)
        → resolveBeanReference(...)
            → beanFactory.getBean(beanName)   컨테이너 캐시에서 반환 (메서드 재실행 없음)
```

(mini) `MiniConfigurationProcessor`는 이 분기 자체가 없다 — `@MiniBean` 메서드를 직접 호출하면 그냥 자바 메서드 호출이고, 파라미터로 선언해야만 `SimpleBeanFactory.instantiateViaFactoryMethod()`의 `resolveArguments()`가 `getBean(parameterType)`으로 해석해서 컨테이너를 거친다.

## 7. 브레이크포인트

이번 주제는 소스 확인(6·9번)과 실제 실행 결과(8번)로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 다음은 추적 후보다.

```text
org.springframework.context.annotation.ConfigurationClassEnhancer$BeanMethodInterceptor#intercept
org.springframework.context.annotation.ConfigurationClassEnhancer$BeanMethodInterceptor#isCurrentlyInvokedFactoryMethod
org.springframework.beans.factory.support.SimpleInstantiationStrategy#instantiate
```

## 8. 런타임 관찰

[`ConfigurationProxyTest`](../../experiments/configuration-proxy-lab/src/test/java/lab/experiments/configproxy/ConfigurationProxyTest.java)로 고정한 결과:

| 설정 방식 | `orderService.getPaymentService() == containerBean` |
| --- | --- |
| `@Configuration` (Full, 기본값) | **true** |
| `@Configuration(proxyBeanMethods = false)` (Lite) | false |
| `@Component` + `@Bean` | false |
| Full 모드의 **static** `@Bean`끼리 직접 호출 | false (static은 절대 프록시 안 됨) |

[`MiniConfigurationProcessorTest`](../../mini-spring/mini-java-config/src/test/java/lab/minispring/javaconfig/MiniConfigurationProcessorTest.java)로 고정한 결과:

| 의존성 선언 방식 | 싱글턴 공유? |
| --- | --- |
| 메서드 파라미터로 선언 (`orderService(PaymentService p)`) | 공유됨 |
| 메서드 안에서 직접 호출 (`new OrderService(paymentService())`) | 공유 안 됨 (매번 새 인스턴스) |

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스로 확인했다 — `ConfigurationClassPostProcessorTests.enhancementIsPresentBecauseSingletonSemanticsAreRespected()`(2주차에서 CGLIB 클래스명 검증 목적으로 이미 인용했지만, 이번엔 같은 테스트의 다른 단언에 주목한다):

```java
Foo foo = beanFactory.getBean("foo", Foo.class);
Bar bar = beanFactory.getBean("bar", Bar.class);
assertThat(bar.foo).isSameAs(foo);
assertThat(beanFactory.getDependentBeans("foo")).contains("bar");
```

`bar.foo`가 `Bar`의 `@Bean` 메서드 안에서 `foo()`를 직접 호출해 얻은 참조인데, 컨테이너의 `foo` 싱글턴과 동일하다는 것을 검증한다 — 우리 `fullModeInterceptsCrossBeanMethodCallsAndPreservesSingleton` 테스트와 정확히 같은 시나리오다. 추가로 `getDependentBeans("foo")`가 `"bar"`를 포함한다는 단언은, `resolveBeanReference()`를 통한 크로스 참조가 단순히 값을 반환하는 데 그치지 않고 빈 사이의 의존 관계로도 기록된다는 것을 보여준다 — 이 부분은 이번 조사에서 더 깊이 들어가지는 않았다(12번 참고).

`isCurrentlyInvokedFactoryMethod`가 참조하는 `SimpleInstantiationStrategy.currentlyInvokedFactoryMethod`는 `ThreadLocal<Method>`로 선언돼 있다 — 컨테이너가 실제로 메서드를 호출하기 직전에 설정되고 호출 직후 제거된다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

**구현한 것** — `mini-spring/mini-java-config` + `mini-container` 확장
- `@MiniConfiguration`/`@MiniBean` 처리기가 설정 클래스를 등록하고 각 `@MiniBean` 메서드를 factory-method `BeanDefinition`으로 변환
- `SimpleBeanFactory`에 factory-method 기반 생성 자체를 새로 추가 — `factoryBeanName`/`factoryMethodName`을 `BeanDefinition`에 얹고, `instantiate()`가 그 경우 리플렉션으로 메서드를 찾아 파라미터를 `getBean(Class)`로 해석한 뒤 호출
- 기존 파이프라인(싱글턴 캐싱, 순환 참조 재진입 감지, `BeanPostProcessor` 체인)을 전혀 건드리지 않고 "생성 방법"만 하나 늘리는 방식으로 재사용 — 새 기능을 위해 새 클래스 계층을 만들지 않았다

**생략한 것** (카탈로그가 명시한 제한사항)
- **CGLIB 설정 클래스 프록시** — `@MiniBean` 메서드 직접 호출을 가로챌 방법이 전혀 없다. 이건 "아직 안 만들었다"가 아니라 "지금 구조로는 원천적으로 불가능하다"에 가깝다 — 리플렉션으로 등록한 `BeanDefinition`은 메서드 호출 시점에 개입할 수 없고, 오직 `getBean()` 경유 호출만 통제할 수 있다.
- static 팩토리 메서드 지원 — `MiniConfigurationProcessor`는 `@MiniBean`을 항상 인스턴스 메서드로만 다룬다(설계 노트, 6주차 문서 참고).
- `@Import`/`ImportSelector`/`ImportBeanDefinitionRegistrar` — 카탈로그의 "추가 실험" 목록에 있었지만 이번 세션 범위 밖으로 미뤘다.

## 11. Spring 설계 의도

- **왜 `ThreadLocal`로 "지금 컨테이너가 부르는 중인가"를 구분하는가**: CGLIB 프록시의 `intercept()`는 메서드가 호출되는 모든 경로(컨테이너의 최초 생성 호출이든, 사용자 코드의 크로스 레퍼런스든)에 공통으로 걸린다. 두 경로를 구분할 다른 방법이 마땅치 않다 — 호출자 스택을 분석하는 것보다, "지금 이 스레드에서 컨테이너가 어떤 메서드를 생성 목적으로 부르는 중인지"를 명시적으로 기록해 두고 대조하는 편이 훨씬 단순하고 스레드 안전하다.
- **왜 크로스 참조를 매번 `getBean()`으로 리다이렉트하는가**: 실제 메서드를 다시 실행하면 새 인스턴스가 생기고 싱글턴이 깨진다. 반대로 크로스 참조를 아예 막아버리면 "설정 클래스 안에서 다른 빈의 생성자에 넘길 값을 얻는" 매우 자연스러운 코드 스타일(생성자 파라미터 대신 메서드 호출로 의존성을 표현하는 것)을 쓸 수 없다. 메서드 호출을 겉으로는 그대로 두고 속에서 컨테이너로 리다이렉트하면, 사용자는 평범한 자바 코드를 쓰면서도 컨테이너의 싱글턴 보장을 그대로 얻는다.
- **왜 Mini 구현은 이 문제를 "풀지" 않고 "드러내기만" 하는가**: 카탈로그가 애초에 이 프로젝트의 목적을 "CGLIB 없이 흉내내기"가 아니라 "직접 구현과 Spring의 차이를 기록하기"로 뒀다. 파라미터 선언과 직접 호출의 결과가 다르다는 것을 축소 구현이 그대로 드러내야, CGLIB이 정확히 어떤 문제를 해결하기 위해 존재하는지가 뚜렷해진다.

## 12. 결론 (예상과 실제의 차이)

- 예상대로였던 것: static `@Bean`은 Full 모드에서도 프록시되지 않는다(2주차 예측이 이번에 객체 동일성으로 재확인됨).
- 예상과 달랐던 것: 프록시가 하는 일이 "이 메서드는 항상 컨테이너로 리다이렉트"가 아니라, **호출 시점에 따라 분기**한다는 것이었다 — 컨테이너 자신의 최초 생성 호출은 그대로 통과시키고, 그 이후의 모든 크로스 참조만 리다이렉트한다. 이 분기가 없으면 최초 생성 자체가 무한히 자기 자신에게 위임되는 순환에 빠질 것이다.
- Mini 구현으로 확인한 것: CGLIB 없이는 "메서드 직접 호출을 가로채는" 것 자체가 불가능하다 — 이는 구현 실력의 문제가 아니라, 순수 자바 언어로는 이미 컴파일된 메서드 호출 지점을 런타임에 가로챌 방법이 없기 때문이다(바이트코드 조작 없이는). 이 한계가 project 13의 "생략한 것"이 왜 "아직 안 했다"가 아니라 "이 구조로는 못 한다"인지를 설명한다.
- 새로 열린 질문: `getDependentBeans` 기반의 빈 의존 관계 추적(9번)과 `@Import` 계열 확장 포인트는 범위 밖으로 남겼다.
