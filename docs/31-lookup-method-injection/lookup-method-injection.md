# @Lookup — self-invocation이 처음으로 문제가 되지 않는 확장점

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`30`](../30-conversion-service/conversion-service.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 11·12·25·26·29번을 거치며 "self-invocation은 프록시를 우회한다"는 원칙을 다섯 번 반복해서 확인했는데, `@Lookup`은 그 원칙이 **처음으로 깨지는** 사례다 - CGLIB를 쓰긴 하지만, 별도 객체로 감싸는 프록시가 아니라 **빈 자신을 상속한 서브클래스로 만들어 버리는** 방식이기 때문이다. 그리고 이 메커니즘은 2주차(`BeanDefinition`)와 4주차(빈 생성)가 다룬 것과 완전히 같은 자리(`createBeanInstance()`)에 걸려 있다는 것도 함께 확인한다.

## 1. 이번 질문

- 싱글턴 빈이 프로토타입 빈을 매번 새로 얻고 싶을 때, 생성자 주입은 왜 안 되는가?
- `@Lookup`은 그 문제를 정확히 어떻게 해결하는가 - 프록시인가, 아니면 다른 무엇인가?
- 추상 클래스도 Spring 빈이 될 수 있는가 - 될 수 있다면 어떤 조건에서인가?
- self-invocation(`this.method()`)이 여기서도 우회를 일으키는가?
- `final` 메서드에 `@Lookup`을 붙이면 어떻게 되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Method Injection")는 "싱글턴 빈 A가 매 호출마다 새 프로토타입 빈 B를 필요로 할 때, 생성자/setter 주입은 B를 한 번만 주입하므로 부족하다"는 문제를 정확히 이렇게 제시하고, `@Lookup`(또는 XML의 `lookup-method`)이 그 해법이라고 설명한다.
- 문서는 "Spring이 CGLIB로 그 메서드를 동적으로 오버라이드한다"고 설명하지만, 그 오버라이드가 **별도의 프록시 객체**를 만드는 것인지 **빈 자신의 클래스**를 바꿔치기하는 것인지는 명시적으로 구분하지 않는다 — 이번 실험은 그 구분을 소스와 실행으로 직접 확인했다.
- `final` 클래스/메서드에는 적용할 수 없다는 제약이 API 문서(`@Lookup` Javadoc)에 명시돼 있지만, "안 된다"는 것이 컴파일 에러인지, 런타임 예외인지, 조용한 무시인지는 밝히지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Lookup`도 결국 프록시 기반일 거라 예상했다 — **틀렸다.** 이건 `AdvisedSupport`/`Advisor` 체계와 아무 관계가 없다 - `BeanDefinition`의 `MethodOverrides`에 등록되고, 빈을 **인스턴스화하는 단계**(4주차의 `createBeanInstance()`)에서 CGLIB 서브클래스로 만들어진다.
- self-invocation은 25·26·29번과 마찬가지로 여기서도 우회를 일으킬 거라 예상했다 — **틀렸다.** `this` 자신이 이미 오버라이드된 CGLIB 서브클래스 인스턴스이므로, `this.nextTicket()`도 정상적으로 오버라이드된 동작을 그대로 탄다.
- 추상 클래스는 Spring 빈이 될 수 없을 거라 예상했다(어쨌든 `new AbstractClass()`는 불가능하므로) — 절반만 맞았다. `@Lookup` 메서드가 하나라도 있으면 컴포넌트 스캔이 그 추상 클래스를 후보로 받아들인다 - CGLIB가 어차피 그 추상 메서드를 구현해 줄 것이기 때문이다.
- `final` 메서드에 `@Lookup`을 붙이면 컨텍스트 시작 시점에 명확한 예외로 막힐 거라 예상했다 — **틀렸다.** 아무 예외도 나지 않는다 - `LookupOverride`는 정상적으로 등록되지만, CGLIB가 그 메서드를 실제로 오버라이드하지 못해서 원래 메서드 본문이 조용히 그대로 실행된다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/lookup-method-lab`](../../experiments/lookup-method-lab)

```java
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Ticket { ... }

@Component
public class NaiveTicketSeller {
    private final Ticket injectedOnce;                 // 생성자 주입 - 싱글턴 생성 시 딱 한 번
    public NaiveTicketSeller(Ticket injectedOnce) { this.injectedOnce = injectedOnce; }
    public Ticket sell() { return injectedOnce; }       // 항상 같은 인스턴스
}

@Component
public abstract class LookupTicketSeller {
    @Lookup
    public abstract Ticket nextTicket();                // 매 호출마다 새 프로토타입

    public Ticket sellViaSelfInvocation() {
        return this.nextTicket();                       // self-invocation인데도 정상 동작
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `@Lookup` | 메서드에 붙이는 애노테이션 - 반환 타입(또는 `value()`로 지정한 빈 이름)에 해당하는 빈을 매 호출마다 새로 조회하겠다는 선언 |
| `AutowiredAnnotationBeanPostProcessor#checkLookupMethods` | `@Lookup`이 붙은 메서드를 찾아 `LookupOverride`를 만들어 `RootBeanDefinition.getMethodOverrides()`에 등록 |
| `MethodOverride`/`LookupOverride` | `BeanDefinition`의 일부 - "이 메서드는 그냥 실행하지 말고 오버라이드해서 다르게 동작시켜라"는 메타데이터(2주차에서 다룬 `BeanDefinition`의 필드 중 하나) |
| `AbstractAutowireCapableBeanFactory#createBeanInstance` | `mbd.hasMethodOverrides()` 여부에 따라 일반 리플렉션 인스턴스화와 CGLIB 인스턴스화 경로가 갈리는 지점(4주차) |
| `SimpleInstantiationStrategy` / `CglibSubclassingInstantiationStrategy` | 전자는 `hasMethodOverrides()`가 거짓일 때의 빠른 경로, 후자(`SimpleInstantiationStrategy`의 하위 클래스)는 참일 때 실제로 CGLIB `Enhancer`로 서브클래스를 만듦 |
| `ClassPathScanningCandidateComponentProvider#isCandidateComponent` | 추상 클래스를 "빈이 될 수 없는 것"에서 "될 수 있는 것"으로 바꿔 주는 특별 규칙(`isAbstract() && hasAnnotatedMethods(Lookup.class)`)이 들어 있는 지점(7주차) |
| (실험) `Ticket` | 프로토타입 스코프 - 매번 새로 만들어졌는지를 `id()`로 구분 |

## 6. 호출 흐름

```text
컴포넌트 스캔 (7주차)
  → ClassPathScanningCandidateComponentProvider#isCandidateComponent(AnnotatedBeanDefinition)
      → metadata.isConcrete() || (metadata.isAbstract() && metadata.hasAnnotatedMethods("...Lookup"))
          (LookupTicketSeller: 추상이지만 @Lookup이 있어서 true - 후보로 등록됨)
          (PlainAbstractNoLookup: 추상이고 @Lookup도 없어서 false - 후보에서 제외)

빈 생성 (4주차)
  → AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors
      → checkLookupMethods(beanClass, beanName)
          → @Lookup 메서드 발견 시 LookupOverride를 만들어
            mergedBeanDefinition.getMethodOverrides().addOverride(override)
  → createBeanInstance(beanName, mbd, args)
      → instantiateBean(beanName, mbd)
          → getInstantiationStrategy().instantiate(mbd, beanName, this)
              → SimpleInstantiationStrategy#instantiate
                  → if (!mbd.hasMethodOverrides())  → BeanUtils.instantiateClass(ctor)  (평범한 리플렉션)
                  → else                            → instantiateWithMethodInjection(...)
                      → CglibSubclassingInstantiationStrategy가 오버라이드한 버전
                          → CGLIB Enhancer로 LookupTicketSeller를 상속한 서브클래스 생성
                          → MethodOverrideCallbackFilter로 @Lookup 메서드에만
                            LookupOverrideMethodInterceptor 콜백 연결
                          → 그 외 메서드(sellViaSelfInvocation() 포함)는 PASSTHROUGH(원본 그대로)

매 nextTicket() 호출:
  LookupOverrideMethodInterceptor#intercept(obj, method, args, mp)
    → this.owner.getBeanProvider(Ticket.class).getObject()   (매번 beanFactory에서 새로 조회)
```

`sellViaSelfInvocation()`은 PASSTHROUGH 콜백이 걸린 평범한 메서드일 뿐이고, 그 메서드 본문 안의 `this`는 이미 이 CGLIB 서브클래스 인스턴스 자신이므로, `this.nextTicket()` 호출도 자연스럽게 `LookupOverrideMethodInterceptor`를 다시 탄다 - AOP 프록시(별도 객체가 대상 객체를 감싸는 구조)와 근본적으로 다른 지점이다.

AOP 프록시 구조(25·26·29번)와 `@Lookup`의 CGLIB 서브클래싱 구조를 나란히 비교한 다이어그램: [`diagrams/lookup-vs-proxy.md`](diagrams/lookup-vs-proxy.md)

## 7. 브레이크포인트

25~30번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor#checkLookupMethods
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBeanInstance
org.springframework.beans.factory.support.SimpleInstantiationStrategy#instantiate
org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy$LookupOverrideMethodInterceptor#intercept
org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#isCandidateComponent
```

## 8. 런타임 관찰

[`LookupMethodTest`](../../experiments/lookup-method-lab/src/test/java/lab/experiments/lookup/LookupMethodTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| 생성자로 프로토타입을 주입받은 `NaiveTicketSeller` | 몇 번을 호출해도 항상 같은 `Ticket` 인스턴스 |
| `@Lookup` 메서드 | 호출마다 다른 `Ticket` 인스턴스 |
| 빈 자신의 실제 클래스 | `LookupTicketSeller.class`가 아니라 그걸 상속한 CGLIB 생성 서브클래스 - `getSuperclass()`만 원본과 일치 |
| self-invocation(`this.nextTicket()`)으로 호출 | 정상적으로 매번 다른 인스턴스 - 우회되지 않음 |
| `@Lookup` 없는 추상 클래스(`PlainAbstractNoLookup`) | 컴포넌트 스캔 후보에서 제외 - 빈으로 등록조차 안 됨(`getBeansOfType`이 빈 맵) |
| `final` 메서드에 붙은 `@Lookup` | 아무 예외 없이 컨텍스트가 정상적으로 뜨지만, 호출하면 원래 메서드 본문(`null` 반환)이 그대로 실행됨 - 오버라이드가 조용히 무시됨 |

**직접 겪은 것**: 다섯 번째·여섯 번째 행은 실행해 보기 전까지 결과를 확신할 수 없었다. `PlainAbstractNoLookup`이 혹시라도 빈 후보로 잘못 등록됐다면 컨텍스트 자체가 뜨지 못했을 것이므로(추상 클래스를 CGLIB 오버라이드 없이 인스턴스화할 방법이 없다), 이 테스트는 다른 다섯 개 테스트 전체가 쓰는 같은 `LookupConfig`를 위험에 빠뜨릴 수도 있는 실험이었다 - 소스(`isCandidateComponent`)를 먼저 읽고 "제외될 것"이라 예상한 뒤에 실행했고, 예상대로 컨텍스트는 정상적으로 떴다. `final` 메서드 쪽은 더 흥미로웠다 - "CGLIB가 실패하면 최소한 로그 경고 정도는 남기지 않을까"라고 예상했는데, `system-err`에도 아무 흔적이 없었다 - `MethodOverrideCallbackFilter`가 애초에 오버라이드 가능한 메서드만 골라 콜백을 연결하고, `final` 메서드는 그 골라내는 단계에서부터 조용히 제외되는 것으로 보인다(9번 절).

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `ClassPathScanningCandidateComponentProvider#isCandidateComponent(AnnotatedBeanDefinition)`의 실제 소스: `return (metadata.isIndependent() && (metadata.isConcrete() || (metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()))));`를 그대로 확인했다 - "추상 클래스는 컴포넌트 스캔에서 제외된다"는 일반적인 이해가, `@Lookup`이라는 아주 구체적인 예외 하나를 갖고 있다는 것을 코드로 직접 봤다. 8번 절 다섯 번째 행의 정확한 근거다.
- `AutowiredAnnotationBeanPostProcessor#checkLookupMethods`의 실제 소스: `@Lookup`이 붙은 메서드를 발견하면 `final`/`private` 여부를 전혀 확인하지 않고 무조건 `LookupOverride`를 만들어 등록한다는 것을 확인했다 - "말이 안 되는 조합"을 이 단계에서 미리 걸러내지 않는다는 뜻이다. `MethodValidationInterceptor`(26번)의 `sync=true` 오용을 컨텍스트 초기화 시점에 `IllegalStateException`으로 막던 것과 대비되는 지점이다 - 여기는 그런 fail-fast 가드가 없다.
- `SimpleInstantiationStrategy#instantiate`의 실제 소스: `if (!bd.hasMethodOverrides())`가 참이면 CGLIB를 아예 거치지 않고 곧장 `BeanUtils.instantiateClass(constructorToUse)`로 끝난다는 것을 확인했다 - `instantiationStrategy` 필드 자체는 항상 `CglibSubclassingInstantiationStrategy`(CGLIB 지원 버전)로 설정돼 있지만(6번 절), `MethodOverrides`가 없는 압도적 다수의 평범한 빈들은 이 조건 분기 덕분에 CGLIB를 전혀 쓰지 않는다 - "무거운 도구를 항상 준비는 해 두되, 필요할 때만 실제로 쓴다"는 패턴이다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-container`가 이미 `BeanDefinition` → 빈 생성이라는 골격을 다뤘고, 이번 주의 진짜 가치는 그 골격 안에 "메서드 하나를 조건부로 오버라이드한다"는 새로운 분기(`hasMethodOverrides()`)가 있다는 것과, 그 분기가 CGLIB 서브클래싱이라서 self-invocation 문제로부터 자유롭다는 **구조적 차이**를 이해하는 데 있었다 - 이건 뼈대를 다시 구현하는 것보다, 기존 뼈대(4주차 `mini-container`) 위에서 실제 Spring과 나란히 비교하는 쪽이 훨씬 명확했다.

## 11. Spring 설계 의도

- **왜 `@Lookup`은 AOP 프록시가 아니라 서브클래싱으로 구현됐는가**: `@Transactional`/`@Cacheable`/`@Async`(25·26·29번)는 "이미 존재하는 호출을 가로챈다"는 문제다 - 프록시가 자연스럽다. 반면 `@Lookup`은 "이 메서드가 호출될 때마다 다른 빈을 새로 찾아 달라"는, 메서드의 **구현 자체를 대신해 달라**는 요청이다 - 추상 메서드는 애초에 본문이 없으므로 가로챌 "원래 동작"조차 없다. 원래 동작을 가로채는 것과 원래 동작을 대신 제공하는 것은 다른 문제이고, Spring은 후자에 서브클래싱이라는 다른 도구를 선택했다 - `@Scheduled`(28번, 리플렉션 직접 호출)에 이어, "문제의 성격이 다르면 확장점의 형태도 달라야 한다"는 이 저장소의 반복된 결론이 여기서 세 번째로 확인된다.
- **왜 이 방식이 self-invocation 문제를 피해 가는가**: AOP 프록시는 "대상 객체"와 "그걸 감싸는 프록시 객체"가 서로 다른 두 개의 객체이기 때문에, 대상 객체 내부에서의 `this` 호출이 프록시를 거치지 않는다. `@Lookup`은 애초에 별도의 감싸는 객체를 만들지 않는다 - Spring이 만드는 유일한 객체가 이미 오버라이드가 적용된 서브클래스이므로, `this`가 가리키는 객체 자체에 오버라이드된 동작이 내장돼 있다. "감싼다"와 "상속해서 대신 만든다"는 이번처럼 비슷해 보이는 문제(런타임에 메서드 동작을 바꾼다)에 대한 서로 다른 해법이고, 그 차이가 self-invocation이라는 아주 구체적인 결과로 드러난다.
- **왜 `final` 메서드에 `@Lookup`을 붙여도 명확한 예외로 막지 않는가**: `checkLookupMethods`는 애노테이션을 등록하는 단계일 뿐, 그 메서드가 실제로 오버라이드 가능한지는 알지 못한다(리플렉션으로 `Modifier.isFinal()`을 확인할 수는 있지만, 굳이 이 단계에서 검증하지 않기로 한 것으로 보인다) - 실제 오버라이드 가능 여부를 판단하는 주체는 CGLIB의 `Enhancer` 자신이고, `Enhancer`는 "오버라이드할 수 없는 메서드는 그냥 건드리지 않는다"는 자바 언어의 당연한 제약을 따를 뿐이다. 그 결과, 잘못된 사용이 컨텍스트 시작 실패라는 시끄러운 신호 대신 "그냥 예상과 다르게 동작한다"는 조용한 신호로 나타난다 - 26번(`sync=true` 오용)이 fail-fast를 택한 것과 달리, 여기는 검증 비용(모든 후보 메서드의 modifier를 확인하고 의미 있는 메시지를 만드는 것)이 이 기능의 상대적 사용 빈도에 비해 우선순위가 낮았을 것으로 보인다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: self-invocation이 여기서는 문제가 되지 않는다는 것 - 11·12·25·26·29번을 거치며 "프록시 기반이면 self-invocation은 항상 위험하다"는 패턴이 거의 반사적으로 느껴질 정도였는데, `@Lookup`은 애초에 "프록시가 아니다"라는 사실 하나로 그 패턴 전체를 벗어났다. 같은 결과(런타임에 메서드 동작이 바뀜)를 만드는 메커니즘이 여러 가지일 수 있고, 그 메커니즘의 차이가 어떤 부작용은 피하고 어떤 부작용은 새로 만드는지가 이번처럼 구체적으로 드러난 것은 처음이었다.
- 예상 밖이었던 것: 추상 클래스가 컴포넌트 스캔 후보가 될 수 있다는 것 - "추상 클래스는 인스턴스화할 수 없으니 당연히 빈이 될 수 없다"는 상식이, `@Lookup`이라는 아주 구체적인 조건 하나로 뒤집힌다는 것을 소스에서 직접 확인했다.
- 예상대로였던 것(재확인): 생성자로 주입받은 프로토타입 빈이 싱글턴 생성 시점에 얼려 버린다는 것 - 이 저장소가 4주차부터 반복해서 다뤄 온 "싱글턴은 컨테이너 생명주기 동안 한 번만 만들어진다"는 원칙이 프로토타입 의존성과 만났을 때 왜 문제가 되는지를 정확히 보여준다.
- 새로 배운 것: `hasMethodOverrides()`라는 분기 하나가, `instantiationStrategy` 필드 자체는 항상 CGLIB 지원 버전으로 고정해 두면서도 실제 CGLIB 사용은 필요한 빈에만 국한시킨다는 것 - "비용이 있는 기능은 켜 두되 조건부로만 실제로 쓴다"는 패턴을, `@Scheduled`(28번)의 `ScheduledAnnotationBeanPostProcessor`가 `getOrder() == LOWEST_PRECEDENCE`로 "가능한 한 늦게 관여한다"는 것과는 또 다른 방식으로 재확인했다.
