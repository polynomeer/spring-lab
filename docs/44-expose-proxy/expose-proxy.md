# AopContext.currentProxy() — self-invocation 문제의 공식 탈출구, 그리고 그 탈출구 자체의 함정

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`43`](../43-bean-wrapper/bean-wrapper.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 11·12·25·26·29번을 거치며 "self-invocation은 프록시를 우회한다"는 것을 다섯 번 넘게 **관찰**만 해 왔는데, 이번 문서는 Spring이 그 문제에 마련해 둔 공식 탈출구 - `exposeProxy` + `AopContext.currentProxy()` - 를 직접 써 보고, 그 탈출구를 쓰다가 실제로 부딪힌 새로운 함정(애노테이션은 구현 메서드에 상속되지 않는다)까지 함께 정리한다.

## 1. 이번 질문

- Spring AOP가 self-invocation 문제를 위해 제공하는 공식적인 해법은 무엇인가?
- `AopContext.currentProxy()`는 어느 스레드, 어느 시점에 "현재 프록시"를 알 수 있는가?
- `exposeProxy`를 켜기만 하면 기존의 `this.method()` 호출도 자동으로 고쳐지는가?
- 이 메커니즘을 선언적 AOP(`@Aspect` + `@EnableAspectJAutoProxy`)에서 쓰면 `@Transactional`/`@Cacheable`(25·26번)과 같은 자동 프록시 생성 경로를 그대로 타는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Advising Beans with the Spring API", `AopContext` Javadoc)는 "AOP 프레임워크가 프록시를 노출하도록 설정돼 있으면 `AopContext.currentProxy()`로 현재 프록시를 얻을 수 있다"고 설명하고, `ProxyFactory#setExposeProxy(true)` 또는 `@EnableAspectJAutoProxy(exposeProxy = true)`로 이걸 켤 수 있다고 안내한다.
- 문서는 "이 방식은 대상 클래스가 Spring AOP에 직접 결합되므로 이상적이지 않다"고 명시적으로 경고한다 - 다른 대안(자기 자신을 명시적으로 주입받는 것 등)을 권장하면서도, self-invocation이 꼭 필요한 소수의 경우를 위한 탈출구로 이 방법을 남겨 둔다.
- `AopContext.currentProxy()`가 노출된 프록시가 없을 때 정확히 무엇을 던지는지는 Javadoc에 예외 타입만 명시돼 있고(`IllegalStateException`), 메시지 내용까지는 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `exposeProxy = true`를 켜기만 하면, 기존 코드의 평범한 `this.method()` 호출도 자동으로 프록시를 거치도록 고쳐질 거라 예상했다 — **틀렸다.** `exposeProxy`는 "현재 프록시를 스레드에 노출해 둔다"는 것뿐이다 - 대상 클래스가 그 노출된 프록시를 `AopContext.currentProxy()`로 **직접 찾아가야만** self-invocation이 어드바이스를 탄다. 기존의 `this.method()` 호출은 여전히 프록시를 우회한다.
- `AopContext.currentProxy()`는 `exposeProxy`가 꺼져 있으면 `null`을 돌려줄 거라 예상했다 — **틀렸다.** `null`을 조용히 돌려주는 대신 `IllegalStateException`을 던진다 - "값이 없다"가 아니라 "이 기능 자체가 설정되지 않았다"는 걸 명확한 실패로 알린다.
- `@LoggedOperation`을 인터페이스 메서드에 붙이면, 구현 메서드도 당연히 그 애노테이션을 "갖고 있는" 것으로 취급될 거라 예상했다 — **틀렸다.** 자바는 애노테이션을 구현 메서드에 자동으로 상속해 주지 않는다 - 이 사실을 몰랐다가 실제로 겪었다(8번 절 "직접 겪은 것" 참고).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/expose-proxy-lab`](../../experiments/expose-proxy-lab)

```java
public class GreeterImpl implements Greeter {
    public String greet() { return "Hello"; }

    public String greetViaPlainSelfInvocation() {
        return this.greet();                                    // 여전히 우회됨
    }

    public String greetViaAopContext() {
        return ((Greeter) AopContext.currentProxy()).greet();    // 프록시를 명시적으로 다시 거침
    }
}
```

```java
ProxyFactory factory = new ProxyFactory(new GreeterImpl());
factory.addAdvice(interceptor);
factory.setExposeProxy(true);
Greeter proxy = (Greeter) factory.getProxy();

proxy.greetViaAopContext();   // 어드바이스가 정상적으로 적용됨
```

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)   // 자동 프록시 생성 경로 전체에 적용
public class ExposeProxyAspectConfig {
    @Bean public LoggingAspect loggingAspect() { return new LoggingAspect(); }
    @Bean public OrderService orderService() { return new OrderServiceImpl(); }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AopContext` | `ThreadLocal<Object>` 하나로 "현재 스레드에서 실행 중인 AOP 프록시"를 보관하는 정적 유틸리티 |
| `AdvisedSupport#exposeProxy` | `ProxyFactory`/`AnnotationAwareAspectJAutoProxyCreator`가 만드는 프록시마다 갖는 boolean 필드 - 기본값 `false` |
| `JdkDynamicAopProxy#invoke`/`CglibAopProxy` | `exposeProxy`가 `true`면 실제 호출 전 `AopContext.setCurrentProxy(proxy)`를, `finally`에서 이전 값 복원을 수행하는 지점 |
| `@EnableAspectJAutoProxy(exposeProxy = true)` | 12번 문서의 `AnnotationAwareAspectJAutoProxyCreator`가 만드는 **모든** 프록시에 `exposeProxy = true`를 일괄 적용하는 선언적 스위치 |
| (실험) `GreeterImpl`/`OrderServiceImpl` | `this.method()`(여전히 우회)와 `AopContext.currentProxy().method()`(정상 적용)를 나란히 비교 |

## 6. 호출 흐름

```text
[exposeProxy = true인 프록시를 통한 모든 호출 - 42번(TaskDecorator)과 같은 "저장 후 복원" 패턴]
JdkDynamicAopProxy#invoke(proxy, method, args)
  → if (this.advised.exposeProxy) {
        oldProxy = AopContext.setCurrentProxy(proxy)     ← 이 스레드의 이전 값을 저장해 두고 교체
    }
  → (어드바이스 체인을 거쳐) 실제 대상 메서드 실행
      → 그 안에서 AopContext.currentProxy() 호출 시
          → ThreadLocal에서 방금 설정해 둔 proxy를 그대로 반환
          → ((Greeter) 그 proxy).greet() 호출 → 이건 다시 프록시를 거치는 새 호출이므로
            어드바이스 체인을 처음부터 다시 탐
  → finally { AopContext.setCurrentProxy(oldProxy) }     ← 이전 값으로 복원(TaskDecorator의 finally와 동일한 목적)

[exposeProxy = false(기본값)인 프록시에서 AopContext.currentProxy() 호출 시]
  → ThreadLocal에 아무것도 없음 → IllegalStateException(exposeProxy를 켜라는 메시지)

[대상 메서드가 여전히 그냥 this.method()를 쓰는 경우]
  → exposeProxy 설정과 무관하게, this는 원래 대상 객체 그대로 → 어드바이스 체인을 아예 안 거침
```

`this.method()`(우회)와 `AopContext.currentProxy().method()`(정상 적용)가 갈리는 지점, 그리고 `exposeProxy`의 저장-복원 구조를 함께 그린 다이어그램: [`diagrams/expose-proxy-flow.md`](diagrams/expose-proxy-flow.md)

## 7. 브레이크포인트

25~43번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어드바이스가 적용되는가/안 되는가"라는 결과였고, `CountingInterceptor`/`LoggingAspect`의 호출 횟수로 직접 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.aop.framework.AopContext#currentProxy
org.springframework.aop.framework.AopContext#setCurrentProxy
org.springframework.aop.framework.JdkDynamicAopProxy#invoke (exposeProxy 분기 - try/finally 구조)
org.springframework.aop.framework.CglibAopProxy (같은 패턴의 CGLIB 버전)
```

## 8. 런타임 관찰

[`ExposeProxyTest`](../../experiments/expose-proxy-lab/src/test/java/lab/experiments/exposeproxy/ExposeProxyTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| `exposeProxy` 기본값(`false`)에서 `this.greet()`로 self-invocation | 어드바이스 우회(카운터 0) |
| 같은 설정에서 `AopContext.currentProxy()` 호출 | `IllegalStateException`("exposeProxy" 언급) |
| `exposeProxy = true` + `AopContext.currentProxy()`로 self-invocation | 어드바이스 정상 적용(카운터 1) |
| `exposeProxy = true`인데도 여전히 `this.greet()`를 쓰는 다른 메서드 | 여전히 우회(카운터 0) - 켜는 것만으로는 기존 코드가 저절로 고쳐지지 않음 |
| `@EnableAspectJAutoProxy(exposeProxy = true)` + 실제 `@Aspect` 선언적 어드바이스 + `AopContext` self-invocation | 정상 적용(카운터 1) - 25·26번과 같은 자동 프록시 생성 경로 위에서도 동일하게 동작 |

**직접 겪은 것**: 다섯 번째 실험을 처음 작성했을 때는 `@LoggedOperation`을 `OrderService` **인터페이스**의 `placeOrder()`에만 붙였다 - 그런데 실행해 보니 `AopContext.currentProxy()`가 여전히 `IllegalStateException`을 던졌다. 처음엔 "`exposeProxy` 설정 자체가 안 먹혔나"라고 의심했는데, 스택 트레이스를 자세히 보니 `JdkDynamicAopProxy` 프레임 자체가 아예 없었다 - 즉 `orderService.placeOrderViaAopContextSelfInvocation()` 호출이 애초에 **프록시를 거치지도 않고** `OrderServiceImpl` 인스턴스를 직접 호출하고 있었다. 원인은 애노테이션이 인터페이스에서 구현 메서드로 자동 상속되지 않는다는 자바의 기본 규칙이었다 - `AbstractAutoProxyCreator`가 프록시를 만들지 말지 판단할 때 `OrderServiceImpl`의 실제 메서드에서 애노테이션을 찾는데, 구현 메서드 자체엔 `@LoggedOperation`이 없으니 어떤 어드바이저도 매칭되지 않아서 이 빈은 **애초에 프록시 대상에서 제외**돼 버린 것이다. `@Override` 메서드에 애노테이션을 직접 다시 붙이고 나서야 정상적으로 프록시가 만들어졌다 - "예상과 다르게 실패했다"가 아니라 "예상한 것과 완전히 다른 지점에서 실패했다"는 걸 스택 트레이스로 정확히 짚어낸 경우였다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `JdkDynamicAopProxy#invoke`의 실제 소스: `if (this.advised.exposeProxy) { oldProxy = AopContext.setCurrentProxy(proxy); setProxyContext = true; }`가 인터셉터 체인 조회(`getInterceptorsAndDynamicInterceptionAdvice`)보다 **먼저**, 그리고 `finally` 블록의 `if (setProxyContext) { AopContext.setCurrentProxy(oldProxy); }`가 대상 재해제(`releaseTarget`)와 나란히 있다는 것을 확인했다 - 이 설정이 "이 메서드에 매칭되는 어드바이스가 있는지"와 무관하게, 프록시를 거치는 **모든** 호출에 대해 무조건 적용된다는 뜻이다(8번 절 실험이 `placeOrderViaAopContextSelfInvocation()` 자신에는 아무 어드바이스가 없어도 문제없이 동작한 이유).
- `AopContext#currentProxy`의 실제 소스: `proxy == null`일 때 `"Cannot find current proxy: Set 'exposeProxy' property on Advised to 'true' to make it available, and ensure that AopContext.currentProxy() is invoked in the same thread as the AOP invocation context."`라는 메시지를 그대로 확인했다 - 이 메시지 자체가 실패 원인을 두 갈래(설정 안 함 / 다른 스레드)로 미리 알려주는, 꽤 친절한 예외 설계라는 것도 함께 확인했다.
- `AopContext#setCurrentProxy`의 실제 소스: `Object old = currentProxy.get(); ...; return old;`로 **이전 값을 반환**한다는 것을 확인했다 - 42번(`TaskDecorator`)의 `RequestContextPropagatingTaskDecorator`가 직접 구현했던 "이전 상태 저장 후 복원" 패턴을, 이번엔 Spring 자신이 프레임워크 코드 안에서 똑같이 쓰고 있다는 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `AopContext`의 핵심(스레드로컬 하나에 "현재 프록시"를 저장했다가 되돌린다)은 42번의 `TaskDecorator`와 사실상 같은 패턴이라 재구현할 새로움이 없다 - 이번 주의 가치는 그 패턴 자체가 아니라, **11·12·25·26·29번이 반복해서 보여준 self-invocation 문제에 대해 Spring이 실제로 제공하는 답**과, 그 답을 실제로 써 보다가 마주친 별개의 함정(애노테이션 상속)에 있었다.

## 11. Spring 설계 의도

- **왜 `exposeProxy`는 기존 `this.method()` 호출을 자동으로 고쳐 주지 않는가**: 만약 자동으로 고쳐 준다면, 그건 바이트코드 조작이나 훨씬 침습적인 계측이 필요하다 - `this.method()`라는 자바 언어 수준의 평범한 메서드 호출을, 런타임에 다른 객체(프록시)로의 호출로 바꿔치기하는 것은 CGLIB 서브클래싱(31번, `@Lookup`)조차 하지 않는 일이다. 대신 Spring은 "노출된 프록시에 접근할 수 있는 통로"(`AopContext.currentProxy()`)만 열어 두고, 그걸 쓸지 말지, 언제 쓸지는 애플리케이션 코드가 명시적으로 결정하게 한다 - 이건 이 저장소가 반복해서 봐 온 "필요한 만큼만 좁게 확장한다"는 원칙의 연장선이지만, 동시에 "이 정도는 사용자 코드가 명시적으로 선택해야 하는 특수한 경우"라는 경계선을 긋는 사례이기도 하다.
- **왜 Spring 공식 문서가 이 방법을 "이상적이지 않다"고 경고하면서도 남겨 두는가**: `AopContext.currentProxy()`를 쓰는 순간 그 클래스는 Spring AOP라는 특정 프레임워크의 API를 직접 참조하게 된다 - 순수한 POJO로 남아 있던 대상 클래스가 프레임워크에 결합되는 것이다. 이건 이 저장소의 실험들(mini-aop, mini-transaction 등)이 계속 지향해 온 "대상 객체는 아무것도 몰라야 한다"는 원칙에 정면으로 반한다. 그런데도 이 탈출구가 존재하는 이유는, self-invocation을 통한 어드바이스 적용이 꼭 필요한 소수의 실제 상황(예: 같은 트랜잭션 서비스 안에서 다른 트랜잭션 경계로 재귀 호출해야 하는 경우)이 있고, 그럴 땐 "설계 원칙을 지키다가 아예 못 하는 것"보다 "원칙을 깨더라도 명시적으로, 의식적으로 깨는 것"이 낫다는 실용적 판단으로 보인다.
- **왜 애노테이션 기반 매칭이 구현 메서드의 애노테이션만 보고, 인터페이스의 것을 자동으로 물려받지 않는가(8번 절)**: 이건 Spring의 설계라기보다 **자바 언어 자체의 규칙**이다 - 애노테이션은 기본적으로 상속되지 않는다(`@Inherited` 메타 애노테이션은 클래스 계층에만 적용되고, 메서드 오버라이드에는 아예 적용되지 않는다). `AbstractAutoProxyCreator`가 이 자바 규칙을 우회해서 인터페이스까지 뒤져 애노테이션을 찾아 줄 수도 있었겠지만, 그러면 "이 메서드에 애노테이션이 있는가"라는 질문의 답이 클래스 하나만 봐서는 알 수 없고 구현하는 인터페이스 전체를 훑어야만 알 수 있는, 훨씬 비용이 크고 예측하기 어려운 규칙이 된다 - 자바의 기본 규칙을 그대로 따르는 쪽이 예측 가능성 면에서 더 안전한 선택이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `exposeProxy = true`가 self-invocation 문제를 "자동으로" 해결해 줄 거라는 기대 - 실제로는 "해결할 수 있는 통로를 열어 줄 뿐"이고, 그 통로를 실제로 쓰는 것은 여전히 대상 코드의 몫이었다. 11·12·25·26·29번에서 계속 봐 온 "self-invocation은 우회된다"는 관찰이, 이번 문서에서도 `exposeProxy`를 켠 것만으로는 전혀 바뀌지 않는다는 걸(네 번째 실험) 확인하고 나서야 이 스위치의 정확한 역할 범위를 이해했다.
- 예상 밖이었던 것: 이번 주제의 가장 큰 함정이 `AopContext` 자체가 아니라 **애노테이션 상속**이라는, 전혀 다른 곳에서 왔다는 것 - self-invocation과 무관한, 자바 언어의 아주 기초적인 규칙 하나를 놓친 것이 겉보기엔 "exposeProxy가 안 먹힌다"는 것과 똑같은 증상(`IllegalStateException`)으로 나타났다. 같은 예외라도 원인은 완전히 다를 수 있다는 걸, 스택 트레이스를 자세히 들여다보고 나서야 구분할 수 있었다.
- 예상대로였던 것(재확인): 저장-복원(`try`/`finally`) 패턴이 스레드로컬 기반 컨텍스트 전파의 표준적인 형태라는 것 - 42번(`TaskDecorator`)에서 직접 구현해 봤던 그 패턴을, 이번엔 Spring 프레임워크 자신의 핵심 코드(`JdkDynamicAopProxy#invoke`) 안에서 똑같이 발견했다.
- 새로 배운 것: `@EnableAspectJAutoProxy(exposeProxy = true)`라는 선언적 스위치 하나가, 12번 문서에서 이미 다룬 자동 프록시 생성 경로(`AnnotationAwareAspectJAutoProxyCreator`) 전체에 일괄 적용된다는 것 - `@Transactional`이든 `@Cacheable`이든 커스텀 `@Aspect`든, 그 프록시가 어떻게 만들어졌는지와 무관하게 `exposeProxy`라는 같은 스위치 하나로 self-invocation 탈출구를 전부 동시에 열 수 있다는 걸 실행으로 확인했다.
