# 프록시와 인터셉터 체인 — 무엇이 프록시되고, 무엇이 되지 않는가

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 11주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 18(Proxy Playground)·프로젝트 19(Mini AOP Framework)에 대응하는 분석 문서다.

**후기**: 10번 절은 원래 "CGLIB 상당의 서브클래스 프록시가 없다"를 의도적 생략으로 남겨 뒀었다(`docs/retrospective/retrospective.md` 7번 절 "남겨 둔 질문"). 카탈로그 32개 프로젝트 + Mini Order Platform 캡스톤을 전부 마친 뒤 그 생략을 실제로 채웠다 — `mini-spring/mini-aop`에 ByteBuddy 기반 `MiniSubclassProxyFactory`를 추가했다. 5·8·10·11번 절에 그 내용을 반영했다.

## 1. 이번 질문

- JDK 프록시와 CGLIB 프록시는 언제 선택되는가?
- Advice는 어떻게 인터셉터로 변환되는가? (이번 주는 `MethodInterceptor` 자체까지만 다루고, `@Aspect`/`Advisor`가 이 인터셉터로 어떻게 변환되는지는 12주차로 미룬다)
- 여러 인터셉터는 어떤 순서로 실행되는가?
- 대상 메서드는 언제 실제로 호출되는가? — 그리고 **호출되지 않는** 경우(self-invocation, final/private 메서드)는 왜 그런가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Core Technologies, "Proxying Mechanisms")은 대상이 인터페이스를 하나라도 구현하면 기본값이 JDK Dynamic Proxy, 그렇지 않으면 CGLIB이라고 설명한다. `proxyTargetClass=true`로 강제로 CGLIB을 쓸 수도 있다.
- 같은 문서는 CGLIB 프록시의 한계를 명시한다 — `final` 메서드는 오버라이드할 수 없어 어드바이스를 걸 수 없고, `private` 메서드는 애초에 프록시 대상이 아니다.
- "self-invocation" 절: 프록시된 빈 내부에서 `this.method()`로 스스로를 호출하면 프록시를 거치지 않으므로 그 호출에는 어드바이스가 적용되지 않는다고 명시한다 — 트랜잭션/캐시/보안 어드바이스에서 실무적으로 가장 자주 부딪히는 함정이라고 설명한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- CGLIB이 인터페이스가 없을 때만 쓰일 것이라 예상했다 — 대체로 맞았지만, `setTargetClass()`로 대상 클래스 자체가 **인터페이스**이면 `proxyTargetClass=true`를 켜도 여전히 JDK 프록시가 만들어진다는 것은 예상 밖이었다(4·9번에서 소스·공식 테스트로 확인).
- equals()/hashCode()도 다른 메서드처럼 인터셉터 체인을 통과할 것이라 예상했다 — **틀렸다.** 대상이 자체 `equals`/`hashCode`를 정의하지 않으면 `JdkDynamicAopProxy#invoke`가 아예 체인을 타지 않고 프록시 자신의 구현으로 바로 처리한다(4·8번).
- 축소 구현(`ReflectiveMethodInvocation`)에서 재시도(retry) 어드바이스를 `invocation.proceed()`로 그냥 구현할 수 있을 거라 예상했다 — 실제로 구현하다가 인덱스가 재사용 불가능한 방식으로 전진한다는 것을 발견했고(8번), 실제 Spring 소스를 다시 확인해서야 왜 그런지, 그리고 실제 Spring은 왜 다른지(11번) 이해했다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/proxy-playground`](../../experiments/proxy-playground)
```java
ProxyFactory pf = new ProxyFactory(new GreetableImpl());   // 인터페이스 있음 → JDK 프록시
pf.addAdvice(interceptor);
Greetable proxy = (Greetable) pf.getProxy();

ProxyFactory pf2 = new ProxyFactory(new PlainGreeter());   // 인터페이스 없음 → CGLIB 프록시
```

**축소 구현** — [`mini-spring/mini-aop`](../../mini-spring/mini-aop)
```java
public final class ReflectiveMethodInvocation implements MethodInvocation {
    private int index = -1;
    public Object proceed() throws Throwable {
        if (++index == interceptors.size()) {
            return method.invoke(target, arguments);
        }
        return interceptors.get(index).invoke(this);
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `DefaultAopProxyFactory` | `AdvisedSupport`를 보고 JDK/CGLIB 중 어느 `AopProxy` 구현체를 만들지 결정 |
| `JdkDynamicAopProxy` | `InvocationHandler` 구현 — equals/hashCode 특수 처리 후 인터셉터 체인 진입 |
| `ObjenesisCglibAopProxy` | Objenesis로 생성자를 우회해 대상 클래스의 서브클래스 인스턴스를 만듦 |
| `ReflectiveMethodInvocation` | 인터셉터 체인 하나의 실행 상태(`currentInterceptorIndex`) + `proceed()` |
| `AopUtils` | `isJdkDynamicProxy`/`isCglibProxy`/`getTargetClass` 등 진단 유틸리티 |
| (mini) `MiniProxyFactory` | JDK Dynamic Proxy만 지원하는 축소된 `AopProxy` 생성기 |
| (mini) `MiniSubclassProxyFactory` | ByteBuddy로 런타임에 실제 서브클래스를 만드는, CGLIB 상당의 축소된 `AopProxy` 생성기(후기 참고) |
| (mini) `ReflectiveMethodInvocation` | 실제 Spring과 이름·구조는 같지만 인덱스 전진 방식이 다름(9·11번) — `MiniProxyFactory`/`MiniSubclassProxyFactory` 둘 다 이 하나를 공유한다 |

## 6. 호출 흐름

```text
[JDK vs CGLIB 선택] - DefaultAopProxyFactory#createAopProxy
  optimize || proxyTargetClass || 사용자 지정 인터페이스 없음?
    → 아니오: JdkDynamicAopProxy
    → 예: targetClass가 null/인터페이스/프록시 클래스/람다인가?
        → 예: JdkDynamicAopProxy (CGLIB로 넘어갈 수 없는 대상)
        → 아니오: ObjenesisCglibAopProxy

[메서드 호출 한 번] - JdkDynamicAopProxy#invoke / CGLIB의 DynamicAdvisedInterceptor#intercept
  equals()/hashCode()이고 대상이 직접 정의하지 않았으면
    → 체인을 타지 않고 프록시 자신의 구현으로 바로 반환
  그 외
    → 이 메서드에 적용되는 인터셉터 목록 조회
    → new ReflectiveMethodInvocation(target, method, args, interceptors)
    → invocation.proceed()
        currentInterceptorIndex가 마지막이면 → method.invoke(target, args) (실제 대상 호출)
        아니면 → ++index 하고 그 인터셉터의 invoke(this) 호출 (재귀적으로 다음 proceed() 유도)
```

인터페이스 선택과 인터셉터 체인 진행을 함께 그린 다이어그램: [`diagrams/proxy-selection-and-chain.md`](diagrams/proxy-selection-and-chain.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 다음은 추적 후보다.

```text
org.springframework.aop.framework.DefaultAopProxyFactory#createAopProxy
org.springframework.aop.framework.JdkDynamicAopProxy#invoke
org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor#intercept
org.springframework.aop.framework.ReflectiveMethodInvocation#proceed
```

## 8. 런타임 관찰

[`ProxyPlaygroundTest`](../../experiments/proxy-playground/src/test/java/lab/experiments/proxy/ProxyPlaygroundTest.java) (9개):

| 실험 | 결과 |
| --- | --- |
| 인터페이스 있는 대상, 기본 설정 | JDK 프록시. `getTargetClass()`는 원본 클래스, `proxy.getClass()`는 원본과 다름 |
| 인터페이스 없는 대상, 기본 설정 | CGLIB 프록시(설정 없이도 자동 전환), 원본 타입으로 캐스팅 가능 |
| 인터페이스 있는 대상 + `proxyTargetClass=true` | CGLIB 프록시로 강제 전환, 원본 타입 `instanceof` 성립 |
| `final` 클래스 | `ProxyFactory#getProxy()`가 `AopConfigException` |
| `final` 메서드 (클래스는 non-final) | 프록시를 거치되 오버라이드가 안 돼 원본이 그대로 호출됨 — **인터셉터를 전혀 거치지 않음** |
| `private` 메서드가 `public` 메서드 내부에서 호출됨 | 인터셉터 기록에 전혀 남지 않음 (오버라이드 대상 자체가 아님) |
| 대상이 `equals`/`hashCode`를 정의하지 않음 | `proxy.equals()`/`hashCode()` 호출이 인터셉터를 거치지 않고 프록시 자신의 구현으로 처리됨 |
| JDK 프록시를 원본 클래스로 캐스팅 | `ClassCastException`. CGLIB 프록시는 캐스팅 성공(서브클래스이므로) |
| CGLIB 프록시 인스턴스의 상속 필드를 리플렉션으로 직접 읽음 | `null`(비어 있음) — `proceed()`가 실제로는 target 객체의 메서드를 호출하기 때문에 상태는 target에 쌓이고, 프록시 인스턴스 자신(Objenesis로 생성자 건너뛰고 만들어진 별개 객체)의 필드는 계속 비어 있음 |
| 프록시된 메서드 내부에서 같은 인터페이스의 다른 메서드를 `this`로 호출(self-invocation) | 내부 호출은 인터셉터 기록에 남지 않음. 외부에서 프록시를 거쳐 같은 메서드를 직접 부르면 정상적으로 기록됨 |

[`MiniAopTest`](../../mini-spring/mini-aop/src/test/java/lab/minispring/aop/MiniAopTest.java) (7개)로 확인한 것:

| 실험 | 결과 |
| --- | --- |
| 인터셉터 1개 | 정상적으로 대상 호출을 감쌈 |
| 인터셉터 3개(Logging → Authorization → Timing) | 카탈로그가 제시한 정확한 순서로 양파처럼 감싸며 실행: `Logging before → Auth before → Timing before → Target → Timing after → Auth after → Logging after` |
| 인증 실패 | `SecurityException`, `proceed()` 자체가 호출되지 않아 "after" 로그가 전혀 안 남음, 대상 메서드도 호출 안 됨 |
| 예외 변환 | `ArithmeticException`(0으로 나누기)을 원하는 런타임 예외로 치환, cause 체인 보존 |
| 재시도, N번째 시도에 성공 | 성공까지 정확히 N번 대상을 호출 |
| 재시도, 끝까지 실패 | 마지막 시도의 예외를 그대로 재던짐, 정확히 `maxAttempts`번만 시도 |

[`MiniSubclassProxyFactoryTest`](../../mini-spring/mini-aop/src/test/java/lab/minispring/aop/MiniSubclassProxyFactoryTest.java) (4개, 후기에서 추가) — CGLIB 상당 서브클래스 프록시로 확인한 것:

| 실험 | 결과 |
| --- | --- |
| 인터페이스 없는 대상 + 오버라이드 가능한 메서드 | 인터셉터를 정상적으로 거치고, 별개 객체인 target의 실제 상태를 봄(8번 절의 CGLIB 실험과 같은 구조) |
| `final` 메서드 | 오버라이드 자체가 불가능해 인터셉터를 전혀 거치지 않음(8번 절의 CGLIB 실험과 동일) — **그런데 그 메서드가 읽는 필드는 target의 것이 아니라 프록시 인스턴스 자신의 것**이었다. 실제 Spring의 CGLIB 프록시는 Objenesis로 생성자 자체를 건너뛰어 그 필드가 `null`/`0`으로 비어 있지만(8번 절 마지막 행), 이 축소 구현은 Objenesis 없이 프록시의 진짜 무인자 생성자를 그대로 호출하므로 "비어 있음" 대신 "target과는 다른, 프록시 자신만의 초기 상태"로 나타난다 — 결론(final 메서드는 target을 보지 않는다)은 같지만 그 결론이 드러나는 구체적인 값은 다르다 |
| `proxy instanceof 원본클래스` | 성립함 — `MiniProxyFactory`(JDK Dynamic Proxy)는 인터페이스가 없는 대상 자체를 프록시할 수 없어서 이 비교 자체가 불가능했다 |
| `final` 클래스를 프록시 시도 | `IllegalArgumentException`("Cannot subclass primitive, array or final types") — 서브클래싱 자체가 언어 차원에서 불가능하다는 게 8번 절의 CGLIB `AopConfigException`과 같은 근본 원인이다. 다만 이 예외는 ByteBuddy가 `.subclass()` 호출 시점에 곧바로 던지는 것으로, `MiniSubclassProxyFactory` 자신은 이 경로를 감싸지도 별도 예외 타입으로 정리하지도 않는다 — 실제로 실행해 보고서야 어떤 타입·메시지인지 확인했다(직접 검증 없이 "ByteBuddy가 뭔가 던지겠지"로 추측만 하고 넘어갔다면 문서에 틀리게 적을 뻔했다) |

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `ProxyFactoryTests`(`spring-aop`)로 확인했다.

- **`proxyTargetClassWithConcreteClassAsTarget()`**: 인터페이스를 구현하지 않는 구체 클래스(`TestBean`)를 대상으로 `ProxyFactory`를 만들면 별다른 설정 없이도 `AopUtils.isCglibProxy()`가 `true` — 우리 `noInterfaceTargetForcesCglibProxy` 테스트와 정확히 같은 시나리오다.
- **`proxyTargetClassWithInterfaceAsTarget()`**: `setTargetClass(ITestBean.class)`로 대상 클래스 자체가 **인터페이스**이면, `AopUtils.isJdkDynamicProxy()`가 `true` — `DefaultAopProxyFactory`의 `targetClass.isInterface()` 분기(6번)를 직접 검증하는 테스트다. 우리 실험에는 이 경우가 없었다 — 소스를 다시 읽다가 발견한, 실무에서 거의 안 부딪히는 엣지 케이스다.
- **self-invocation을 직접 검증하는 이름의 공식 테스트는 찾지 못했다** — 정직하게 밝혀 둔다. 레퍼런스 매뉴얼에는 명시돼 있지만(2번), 이 자체를 단정하는 단위 테스트는 여러 통합 테스트(`@Transactional` self-invocation이 커밋되지 않는 것을 검증하는 트랜잭션 테스트 등, 13주차 예정)에 간접적으로만 나타나는 것으로 보인다.
- **`final` 메서드/`equals`·`hashCode` 특수 처리를 직접 검증하는 이름의 공식 테스트도 찾지 못했다** — `JdkDynamicAopProxy`/`CglibAopProxy` 소스 자체(4·6번에서 인용)로 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-aop`(project 19) — 새 모듈로 만들었다(project 15·9와 달리 기존 `mini-container`를 확장하지 않은 이유: AOP는 빈 생성 파이프라인과 독립적인 별개의 관심사라, 인터셉터 체인 자체를 순수하게 검증하는 데는 `BeanFactory`가 필요 없다).

**구현한 것**
- `MethodInterceptor`/`MethodInvocation`/`ReflectiveMethodInvocation` — 이름과 구조를 실제 `org.aopalliance.intercept`/Spring의 대응 타입과 최대한 맞춤
- `MiniProxyFactory` — `java.lang.reflect.Proxy` 기반 JDK Dynamic Proxy만 생성
- 어드바이스 6종(카탈로그 요구사항 전부): `LoggingInterceptor`, `AuthorizationInterceptor`, `TimingInterceptor`, `RetryInterceptor`, `ExceptionTranslationInterceptor`, `CallCountInterceptor`
- **(후기에서 추가) `MiniSubclassProxyFactory`** — ByteBuddy(`net.bytebuddy:byte-buddy`)로 대상 클래스의 실제 서브클래스를 런타임에 생성해서, 인터페이스가 없는 대상도 프록시한다. ASM을 직접 다루는 새 바이트코드 생성기를 짜는 대신 실제 라이브러리를 그대로 가져다 썼다 — 실제 Spring도 CGLIB을 재구현하지 않고 벤더링(`org.springframework.cglib.*`)해서 쓴다는 점에서 같은 선택이다. `MethodInterceptor`/`ReflectiveMethodInvocation`은 `MiniProxyFactory`와 완전히 동일한 것을 그대로 재사용한다 — 달라지는 건 오직 "프록시 인스턴스를 어떻게 만드는가"뿐이다.

**생략한 것 (의도적)**
- **`MiniSubclassProxyFactory`는 Objenesis 없이 대상의 실제 무인자 생성자를 그대로 호출한다** — 실제 Spring의 `ObjenesisCglibAopProxy`는 Objenesis로 생성자 자체를 건너뛰어 프록시 인스턴스의 필드를 전부 비운 채(`null`/`0`) 시작한다. 이 축소 구현은 그 라이브러리를 추가로 끌어오지 않고 그냥 진짜 생성자를 호출한다 — 그래서 대상 클래스에 `public` 무인자 생성자가 없으면 프록시를 만들 수 없다는 제약이 생겼고, `final` 메서드가 보는 "프록시 자신의 상태"도 Spring처럼 완전히 비어 있는 게 아니라 그 생성자가 만들어 낸 값이다(8번 절의 새 실험 결과 참고).
- **`equals`/`hashCode`를 특별 취급하지 않는다** — 실제 Spring의 `CglibAopProxy`/`JdkDynamicAopProxy`는 대상이 직접 재정의하지 않은 `equals`/`hashCode` 호출을 인터셉터 체인 밖에서 처리한다(2·8·11번 절). `MiniSubclassProxyFactory`는 `isPublic().and(not(isFinal())).and(not(isStatic()))`로만 오버라이드 대상을 고르는데, `Object`의 `equals`/`hashCode`/`toString`은 이 조건에 그대로 들어맞아서(둘 다 public, non-final, non-static) 대상이 재정의했든 안 했든 무조건 인터셉터 체인을 탄다 — Spring이 의도적으로 막아 둔 부수효과(11번 절 "왜 equals/hashCode는 체인을 건너뛰는가")를 이 축소 구현은 막지 않는다.
- **재시도 어드바이스가 `proceed()`를 쓰지 않는다** — 여기서 실제 Spring과의 흥미로운 차이를 하나 발견했다: 우리 `ReflectiveMethodInvocation.proceed()`는 `++index == interceptors.size()`처럼 **매번 무조건 인덱스를 전진**시키기 때문에, 마지막 인터셉터가 `proceed()`를 두 번째로 호출하면 인덱스가 리스트 범위를 벗어나 `IndexOutOfBoundsException`이 난다. 그런데 실제 Spring의 `ReflectiveMethodInvocation#proceed()`(`spring-aop`)는 **먼저 "이미 마지막 인덱스인가"를 검사하고, 마지막이면 인덱스를 건드리지 않고 바로 `invokeJoinpoint()`만 호출**한다 — 그래서 체인의 가장 안쪽(target에 가장 가까운) 인터셉터가 `proceed()`를 여러 번 불러도 매번 안전하게 대상 메서드가 다시 호출된다. 이 "검사 후 조건부 증가" vs "무조건 증가 후 검사" 차이 하나가 재시도 패턴을 `proceed()`로 표현할 수 있는지 여부를 가른다. 우리 `RetryInterceptor`는 이 차이를 미처 재현하지 못해 `invocation.proceed()` 대신 `invocation.getMethod().invoke(...)`로 대상을 직접 호출하도록 우회했다 — 그 결과 이 인터셉터는 반드시 체인의 가장 안쪽에 있어야 한다는 제약이 생겼다(코드 주석에 남겨 둠).

## 11. Spring 설계 의도

- **왜 CGLIB이 "대상이 인터페이스면" 예외적으로 JDK로 돌아가는가**: CGLIB은 서브클래싱으로 프록시를 만드는데, 인터페이스는 애초에 서브클래싱(상속) 대상이 아니다 — `class Foo extends SomeInterface`는 성립하지 않는다. `DefaultAopProxyFactory`가 `targetClass.isInterface()`를 명시적으로 검사하는 것은 "CGLIB을 강제로 켜도 물리적으로 불가능한 조합은 조용히 JDK로 우회시킨다"는, 설정 실수를 실패로 만들지 않으려는 방어적 설계다.
- **왜 `equals`/`hashCode`는 체인을 건너뛰는가**: 프록시 객체 두 개(또는 프록시와 원본)를 컬렉션(`HashSet`, `HashMap` 키 등)에 넣고 비교하는 일은 흔하다. 만약 `equals()` 호출마다 전체 인터셉터 체인(로깅, 트랜잭션, 캐싱 등)이 실행된다면, 컬렉션 연산 하나가 예측 불가능한 부수효과를 낳을 수 있다. Spring은 이를 막기 위해 대상이 **직접** `equals`/`hashCode`를 재정의한 경우에만 체인을 태우고, 그렇지 않으면(즉 `Object`의 기본 구현을 그대로 쓰는 경우) 프록시 정체성 자체로 비교를 끝낸다 — "이 필드/인스턴스로 식별되는 객체인가"라는 `equals`의 원래 의미를 프록시 계층에서 조용히 보존하는 것이다.
- **왜 `ReflectiveMethodInvocation`은 "검사 후 조건부 증가" 방식을 택했는가**: 이 설계 덕분에 체인의 마지막 인터셉터는 대상 메서드를 여러 번 안전하게 재호출할 수 있다(재시도, 여러 결과를 합치는 어드바이스 등). 반대로 체인 중간의 인터셉터가 `proceed()`를 여러 번 부르면 그다음 인터셉터부터 다시 실행되므로 의도가 다르면 위험할 수 있다 — 하지만 그건 인터셉터 작성자의 책임이지, 프레임워크가 구조적으로 막을 문제는 아니라고 본 것이다. 10번에서 확인했듯, 이 설계를 놓치면(우리 mini처럼 무조건 증가시키면) 표현할 수 있는 어드바이스의 종류 자체가 줄어든다.
- **왜 self-invocation은 고쳐지지 않는 "설계상의" 한계인가**: 프록시는 항상 "바깥에서 안으로" 들어오는 호출만 가로챌 수 있다 — 프록시 객체와 그 프록시가 감싼 원본 인스턴스는 별개의 객체이고, 원본 인스턴스 내부에서의 `this` 호출은 그 원본 인스턴스로 직접 가지 프록시로 돌아 나가지 않는다. AspectJ 컴파일 타임 위빙처럼 바이트코드 자체를 바꾸지 않는 한(런타임 프록시 방식으로는) 구조적으로 고칠 수 없다 — 그래서 Spring은 이를 "버그"가 아니라 "프록시 기반 AOP를 선택했을 때 받아들여야 하는 제약"으로 문서화한다.
- **(후기에서 추가) 왜 실제 Spring은 Objenesis로 생성자를 건너뛰는가**: `MiniSubclassProxyFactory`를 만들며 직접 겪은 이유로 거꾸로 이해했다 - 생성자를 그대로 실행하면 (1) 대상 클래스에 `public` 무인자 생성자가 없는 모든 클래스를 프록시할 수 없게 되고, (2) 그 생성자가 뭔가 부수효과(로그, 외부 자원 초기화 등)를 갖고 있다면 프록시를 "만들기만" 해도 그 부수효과가 실제 대상과 무관하게 한 번 더 일어난다. Objenesis는 JVM이 역직렬화에 쓰는 것과 같은 메커니즘(생성자를 호출하지 않고 힙에 객체만 할당)으로 이 두 문제를 원천적으로 없앤다 - 대신 그 대가로 "프록시 인스턴스 자신의 필드는 항상 완전히 비어 있다"는 성질(final 메서드가 그 빈 상태를 본다는, 8번 절에서 이미 확인한 함정)을 감수한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 재시도 어드바이스를 직접 만들어 보면서 우리 mini의 `proceed()`가 실제 Spring과 미묘하게 다른 전진 방식을 쓴다는 것을 발견했다 — 겉보기엔 거의 동일한 코드(`index`, `interceptors.get(index).invoke(this)`)인데도, "무조건 증가 후 비교" vs "비교 후 조건부 증가"라는 한 줄 차이가 "체인 마지막에서 `proceed()`를 반복 호출할 수 있는가"라는 표현력 전체를 가른다.
- 예상대로였던 것: 인터페이스 유무에 따른 JDK/CGLIB 선택, `final`/`private` 메서드가 어드바이스를 우회하는 것, self-invocation이 프록시를 거치지 않는 것 — 전부 레퍼런스 매뉴얼에 명시된 그대로 재현됐다.
- 예상 밖이었던 것: `equals`/`hashCode`가 체인을 건너뛴다는 것, 그리고 대상 클래스 자체가 인터페이스면 `proxyTargetClass=true`를 켜도 여전히 JDK 프록시가 된다는 것(공식 테스트 `proxyTargetClassWithInterfaceAsTarget`로 확인) — 둘 다 소스를 읽기 전에는 짐작하지 못했다.
- 다음 주로 넘어갈 질문: 이번 주는 인터셉터 체인 자체(순수한 `MethodInterceptor`/`ProxyFactory` 레벨)만 다뤘다 — `@Aspect`/`@Around`가 어떻게 이 인터셉터로 변환되는지, `AbstractAutoProxyCreator`가 컨테이너의 어느 단계에서 어떤 빈을 프록시 대상으로 판단하는지는 12주차(자동 프록시 생성과 self-invocation)로 이어진다.
- **(후기에서 추가) 이 절에서 "생략했다"고 적어 둔 것이 반드시 "다시 다룰 필요가 없다"는 뜻은 아니었다**: CGLIB 상당 서브클래스 프록시는 10번 절에 "학습 목표 밖"이라고 적어 뒀지만, 실제로 만들어 보니 인터셉터 체인 로직(`ReflectiveMethodInvocation`)은 단 한 줄도 새로 필요 없었고 - 정말 새로 배운 건 "프록시 인스턴스 생성 방식이 target의 상태와 어떻게 분리되는가"(Objenesis 유무) 하나였다. 처음 이 문서를 쓸 때는 이게 "바이트코드 생성 영역이라 어렵다"로 뭉뚱그려졌는데, 실제로 부딪혀 보니 어려운 부분(바이트코드 생성)은 라이브러리가 대신 해 주고, 이 저장소가 정말 가르치려는 부분(인터셉터 체인, 그리고 그 체인이 무엇을 못 보는가)은 기존 코드를 그대로 재사용할 수 있었다.
