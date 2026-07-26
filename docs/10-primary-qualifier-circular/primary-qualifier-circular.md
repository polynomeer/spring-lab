# 순환 참조 — 조기 노출이 통하는 경우와 안 통하는 경우

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 10주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 16(Circular Dependency Laboratory)·프로젝트 17(Mini Cycle Detector)에 대응하는 분석 문서다.

## 1. 이번 질문

로드맵 10주차는 4가지 질문을 던지지만, 그중 "후보가 여러 개일 때 우선순위는 무엇인가?"는 [`09-dependency-resolution.md`](../09-dependency-resolution/dependency-resolution.md) 6·8·11번에서 `@Primary`/`@Qualifier`/이름 일치 우선순위 체인으로 이미 실험·확인했다. 이번 주는 나머지 세 질문에 집중한다.

- 생성자 순환 참조는 왜 해결하기 어려운가? (1주차에서 소스로 확인했지만, 실제 `@Component`/`@Autowired` 스타일로 재현한 적은 없었다)
- 초기 참조(early reference)와 완성된 빈은 어떻게 다른가? — 특히 `@Lazy` 프록시가 낀 경우
- 프록시(AOP)가 포함된 순환 참조는 왜 복잡한가? (6주차에서 `getEarlyBeanReference` 소스를 읽고 "이론적으로 이렇게 될 것"이라 정리했지만, 실행해서 확인한 적은 없었다 — 이번 주에 그 빚을 갚는다)

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Core Technologies, "Circular dependencies")은 "주로 생성자 주입을 쓰면 해결 불가능한 순환 참조 시나리오를 만들 수 있다"고 명시하고, setter/필드 주입으로 우회하거나 `@Lazy`, `ObjectProvider`로 순환을 끊을 것을 권한다.
- `@Lazy`의 javadoc(`org.springframework.context.annotation.Lazy`)은 필드/파라미터에 붙이면 "실제 대상이 필요해지는 시점까지 초기화를 지연시키는 프록시"가 주입된다고 설명한다.
- 순환 참조 해결의 소스 레벨 메커니즘(`earlySingletonObjects`/`singletonFactories`/`getEarlyBeanReference`) 자체는 1주차·6주차에서 이미 상세히 읽었다 — 이번 문서는 그 이해를 실행으로 검증하는 데 집중한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Lazy`를 양쪽 생성자 파라미터에 모두 붙이면 순환이 더 확실하게 끊길 것이라 예상했다 — **틀렸다.** 오히려 두 프록시가 서로를 감싸면서 `isSameAs` 같은 참조 동일성 검증이 깨졌다(4번·8번에서 자세히).
- AOP 프록시가 낀 순환 참조에서는 초기 참조로 노출되는 객체가 "원본"이고, 나중에 완성된 싱글턴이 "프록시"라서 둘이 다른 객체일 것이라 예상했다 — 6주차에서 소스를 읽고 이미 "아니다, `getEarlyBeanReference`가 프록시를 미리 만들어 캐싱하기 때문에 둘은 같은 객체다"라고 결론 내렸었는데, 이번에 실행으로 그 결론이 맞다는 것을 확인했다(예상이 아니라 검증).
- Mini Cycle Detector(project 17, 4주차)는 순환을 "탐지해서 예외를 던지는" 방식이라, 이번 실험에서 setter 순환 참조가 실제 Spring에서는 성공하는데 mini에서는 실패할 것이라 예상했다 — 맞았다(10번에서 확인).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/circular-dependency-lab`](../../experiments/circular-dependency-lab)

생성자 순환 — 실패:
```java
@Component
public class ConstructorCircularA {
    public ConstructorCircularA(ConstructorCircularB b) { ... }
}
@Component
public class ConstructorCircularB {
    public ConstructorCircularB(ConstructorCircularA a) { ... }
}
```

`@Lazy`는 한쪽에만 — 성공, 어느 순서로 만들어져도 무관:
```java
@Component
public class LazyCircularA {
    public LazyCircularA(LazyCircularB b) { this.b = b; }   // 평범한 생성자 의존성
}
@Component
public class LazyCircularB {
    public LazyCircularB(@Lazy LazyCircularA a) { this.a = a; }  // 이쪽만 지연 프록시
}
```

AOP 프록시가 낀 순환 (setter 기반) — 성공, 조기 참조가 최종 등록된 프록시와 동일 객체:
```java
@Aspect
@Component
public class LoggingAspect {
    @Around("execution(* lab.experiments.circular.ProxiedCircularA.*(..))")
    public Object logAround(ProceedingJoinPoint jp) throws Throwable { return jp.proceed(); }
}
```

**축소 구현** — [`mini-spring/mini-container`](../../mini-spring/mini-container) (project 17, 4주차에 선반영)
```java
private Object createBean(String name, BeanDefinition definition) {
    if (beanCreationPath.contains(name)) {
        throw new CircularDependencyException(describePath(name));
    }
    beanCreationPath.addLast(name);
    try {
        return doCreateBean(...);
    } finally {
        beanCreationPath.removeLast();
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `DefaultSingletonBeanRegistry` | 3단계 캐시(`singletonObjects`/`earlySingletonObjects`/`singletonFactories`)로 조기 노출을 관리 (1주차에서 상세히 다룸) |
| `AbstractAutowireCapableBeanFactory#getEarlyBeanReference` | `addSingletonFactory`에 등록되는 팩토리가 실제로 호출될 때 실행되는 조기 참조 생성 로직 (6주차) |
| `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference` | `AbstractAutoProxyCreator`가 구현 — 조기 참조 시점에 이미 프록시를 만들어 캐싱 (6주차) |
| `ContextAnnotationAutowireCandidateResolver` | `@Lazy` 감지(`isLazy`) 및 `buildLazyResolutionProxy`로 지연 프록시 생성 |
| `LazyDependencyTargetSource` (`ContextAnnotationAutowireCandidateResolver`의 내부 클래스) | `TargetSource#getTarget()`에서 실제 빈을 최초 접근 시점에 조회하고 캐싱 |
| (mini) `SimpleBeanFactory#beanCreationPath` | `ArrayDeque` 기반 재진입 감지 — 조기 노출 캐시가 없으므로 재진입 자체를 예외로 처리 |

## 6. 호출 흐름

네 가지 경로의 분기: [`diagrams/circular-reference-paths.md`](diagrams/circular-reference-paths.md)

```text
[생성자 순환 — 실패]
createBean(A) → doCreateBean(A) → 인스턴스 생성 전이라 addSingletonFactory 호출 불가
  → populateBean(A) 단계에서 B를 생성자로 요구
  → createBean(B) → doCreateBean(B) → B의 생성자가 A를 요구
    → getBean(A) → "A는 singletonsCurrentlyInCreation에는 있지만 아직 조기 참조를 노출할 방법이 없다"
    → 생성자 인자 자리에는 완성되지 않은 인스턴스를 넘길 수 없음 → 조회 실패
  → BeanCurrentlyInCreationException → UnsatisfiedDependencyException으로 래핑되어 A까지 전파

[setter 순환 — 성공]
createBean(A) → 인스턴스 생성(생성자는 인자 없음) → addSingletonFactory(A, () -> getEarlyBeanReference(A))
  → populateBean(A)에서 setter로 B 요구 → createBean(B) → 인스턴스 생성 →
    addSingletonFactory(B, ...) → populateBean(B)에서 setter로 A 요구
    → getBean(A) → singletonFactories에 있는 A의 팩토리 실행 → getEarlyBeanReference(A) 호출
      → (AOP 프록시 대상이 아니면) 원본 인스턴스 그대로 반환, earlySingletonObjects로 승격
    → B의 setter에 그 조기 참조 주입, B 완성
  → A의 setter에 완성된 B 주입, A 완성 → A가 최종 등록되는 인스턴스는 조기 참조로 넘겨준 것과 동일 객체

[@Lazy 한쪽 — 성공, 순서 무관]
B의 생성자 파라미터가 @Lazy → doResolveDependency가 실제 조회 대신
  ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy 호출
  → LazyDependencyTargetSource를 가진 CGLIB/JDK 프록시를 "즉시" 만들어 생성자에 주입
  → 실제 A 조회는 프록시의 첫 메서드 호출 시점(getTarget())까지 지연
  → 어느 쪽이 먼저 만들어지든 A를 만드는 시점에는 B가 이미 있거나(B 먼저) B를 만드는 시점에
    A 자리가 프록시라 즉시 채워지므로(A 먼저) 순환 자체가 발생하지 않음

[AOP 프록시 + 순환(setter) — 성공, 조기 참조 == 최종 등록 인스턴스]
createBean(A) → 원본 인스턴스 생성 → addSingletonFactory(A, () -> getEarlyBeanReference(A, mbd, 원본))
  → populateBean(A)에서 B 요구 → createBean(B) → populateBean(B)에서 A 요구
    → getBean(A) → singletonFactories의 팩토리 실행
      → AbstractAutowireCapableBeanFactory#getEarlyBeanReference
        → AbstractAutoProxyCreator#getEarlyBeanReference(원본, "A")
          → wrapIfNecessary(원본, "A", cacheKey) → 프록시 생성, earlyBeanReferences.put(cacheKey, 원본)
      → B는 이 "프록시"를 조기 참조로 받아 저장
  → A의 postProcessAfterInitialization 단계에서 AbstractAutoProxyCreator가
    earlyBeanReferences.remove(cacheKey) == 원본이면 "이미 조기 참조 시점에 프록시로 감쌌다"고
    판단 → 다시 감싸지 않고 그 프록시를 그대로 최종 싱글턴으로 등록
  → 결과: B가 들고 있는 조기 참조와 컨테이너에 최종 등록된 A가 정확히 같은 프록시 객체
```

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 다음은 추적 후보다 — 특히 `getEarlyBeanReference`가 두 단계(팩토리 등록 → 실제 호출)로 나뉘어 있어서, 실행 순서를 눈으로 보면 6번의 흐름표보다 이해가 빠를 것이다.

```text
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#getEarlyBeanReference
org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#getEarlyBeanReference
org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy
org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(String, boolean)
```

## 8. 런타임 관찰

[`CircularDependencyTest`](../../experiments/circular-dependency-lab/src/test/java/lab/experiments/circular/CircularDependencyTest.java) (4개)로 고정한 결과:

| 케이스 | 주입 방식 | 결과 |
| --- | --- | --- |
| 생성자 순환 (`ConstructorCircularA`/`B`) | 둘 다 생성자 | `context.refresh()`에서 `UnsatisfiedDependencyException` |
| setter 순환 (`SetterCircularA`/`B`) | 둘 다 `@Autowired` setter | 성공, `a.getB() isSameAs b`이고 `b.getA() isSameAs a` |
| `@Lazy` 한쪽만 (`LazyCircularA`/`B`) | 둘 다 생성자, B→A만 `@Lazy` | 성공. `a.getB()`는 실제 `LazyCircularB` 그대로(`isSameAs b`, 클래스 일치). `b.getA()`는 프록시(클래스가 `LazyCircularA`와 다름)지만 `b.getA().getB() isSameAs b`로 위임은 정확 |
| AOP 프록시 + setter 순환 (`ProxiedCircularA`/`B`) | 둘 다 setter, A만 `@Aspect` 어드바이스 대상 | 성공. `AopUtils.isAopProxy(a)`는 true, `b.getA() isSameAs a`(조기 참조 == 최종 등록 프록시), `a.getB() isSameAs b`, `a.greet()`는 어드바이스를 거쳐 정상 동작 |

`@Lazy` 테스트에서 예상 밖의 실패를 하나 겪었다: 처음엔 양쪽 생성자 파라미터에 모두 `@Lazy`를 붙이고 `a.getB().getA() isSameAs a`를 검증하려 했는데, `AssertionError`가 났다 — `toString()`은 똑같은데 `isSameAs`가 실패했다. 원인은 한쪽이 `context.getBean()`으로 얻은 실제 싱글턴이고 다른 한쪽은 그 싱글턴을 감싸는 지연 프록시라서 — `@Lazy` 프록시는 대상과 절대 참조 동일(reference-equal)하지 않는다(위임만 투명할 뿐). 그래서 한쪽만 `@Lazy`로 바꾸고, 검증도 "실제 타입 쪽은 `isSameAs` + 클래스 일치", "프록시 쪽은 클래스 불일치 + 위임 결과로 기능 검증"으로 나눠 다시 작성했다.

## 9. 공식 테스트 분석

- **`DefaultListableBeanFactoryTests#circularReferenceThroughAutowiring`/`extensiveCircularReference`/`prototypeCircleLeadsToException`** (1주차에서 이미 인용): 생성자 순환은 실패, setter(property) 순환은 1000개 빈이라도 성공, prototype 순환은 캐시 슬롯이 없어 실패 — 이번 `ConstructorCircularA/B`, `SetterCircularA/B` 테스트는 이 공식 테스트를 `@Component`/`@Autowired` 스타일로 다시 확인한 것이다(원본은 `RootBeanDefinition`을 직접 구성).
- **`LazyAutowiredAnnotationBeanPostProcessorTests#doTestLazyResourceInjection`**: `@Lazy` 대상 빈이 `bf.containsSingleton("testBean")`으로 확인했을 때 최초에는 `false`(아직 안 만들어짐)이고, 프록시를 통해 첫 접근을 한 뒤에야 `true`가 된다는 것, 그리고 실제 빈에 값을 설정(`tb.setName("tb")`)하면 프록시를 통한 조회(`bean.getTestBean().getName()`)에도 그 값이 그대로 보인다는 것(`isSameAs`)을 검증한다 — 우리의 "`b.getA()`는 프록시라 클래스는 다르지만 위임은 정확하다"는 관찰과 정확히 같은 메커니즘이다.
- **`AroundAdviceCircularTests#bothBeansAreProxies`** (`spring-context`의 `org.springframework.aop.aspectj` 패키지): AspectJ 스타일 어드바이스가 걸린 두 빈이 서로를 순환 참조할 때 `AopUtils.isAopProxy()`로 양쪽 모두 프록시임을 확인한다 — 다만 조기 참조와 최종 등록 인스턴스의 동일성(`isSameAs`)까지는 검증하지 않는다. 우리 `aopProxiedCircularReferenceExposesTheSameProxyEarly` 테스트가 `b.getA() isSameAs a`로 그 동일성까지 확인한다는 점에서 이 공식 테스트보다 한 단계 더 깊게 들어간 것이다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-container` — project 17(Mini Cycle Detector)은 4주차에서 이미 만들어져 있어 이번 주에 재구현하지 않았다. 대신 실제 Spring과 나란히 놓고 무엇이 다른지 확인했다.

**구현한 것 (4주차, 재확인만)**
- `beanCreationPath`(`ArrayDeque<String>`)로 생성 재진입을 감지 — `createBean`이 시작될 때 이미 경로에 있으면 `CircularDependencyException`을 생성 경로 메시지(`orderService -> paymentService -> orderService`)와 함께 던짐

**생략한 것 (의도적)**
- **3단계 캐시(`earlySingletonObjects`/`singletonFactories`) 자체가 없다** — mini는 순환을 "해결"하지 않고 "탐지해서 거부"하는 것이 project 17의 목표였다(카탈로그 원문: "순환 참조를 해결하기보다 정확하게 탐지한다"). 그 결과 실제 Spring에서는 성공하는 **setter 순환 참조도 mini에서는 실패한다** — `beanCreationPath`는 주입 방식을 구분하지 않고 재진입 자체를 막기 때문이다. mini의 `populateBean()`도 아직 필드/세터 주입을 구현하지 않은 빈 단계라(9번 문서, 10번 절 참고) 어차피 setter 순환을 재현할 방법이 없다.
- **`@MiniLazy` 같은 지연 프록시 메커니즘이 없다** — mini는 `getBean(Class)`/`resolveArgument()`가 항상 즉시 해석하므로 `@Lazy`로 순환을 끊는 패턴 자체를 표현할 수 없다.
- **mini에는 AOP/프록시 개념이 아직 없다** — `getEarlyBeanReference`에 해당하는 확장점이 없으므로 "조기 참조가 프록시인 경우"는 재현 대상에서 제외했다.

이 차이 자체가 설계 의도를 보여준다: **"탐지"와 "해결"은 전혀 다른 난이도의 문제**다. 재진입 감지는 `Set`/`Deque` 하나로 충분하지만, "완성되지 않은 객체를 안전하게 조기 노출하고, 나중에 그 노출된 참조가 최종 프록시와 일치하도록 보장"하는 것은 3단계 캐시 + `BeanPostProcessor` 확장점 + `ThreadLocal` 없는 캐시 공유(6주차)까지 필요한, 훨씬 큰 기계 장치다.

## 11. Spring 설계 의도

- **왜 생성자 순환은 구조적으로 해결이 불가능한가**: 조기 노출은 "아직 초기화 중인(프로퍼티가 안 채워진) 인스턴스"를 미리 꺼내 쓸 수 있게 해 주는 메커니즘이다. 그런데 생성자 주입은 인스턴스가 존재하기 **이전**에 그 인자가 확정되어야 한다 — 즉 조기 노출할 "인스턴스 자체"가 아직 없다. setter/필드 주입은 "인스턴스는 이미 있고 값만 나중에 채운다"는 시점 분리가 가능하지만, 생성자 주입은 그 시점 분리 자체가 불가능하다. 이건 구현의 한계가 아니라 "생성"과 "초기화"라는 두 단계가 애초에 이 방식에서는 하나로 합쳐져 있기 때문이다.
- **왜 `@Lazy` 프록시는 대상과 동일 객체가 아니어도 괜찮은가**: `@Lazy`가 주는 계약은 "이 의존성처럼 동작하는 무언가"이지 "이 의존성 그 자체"가 아니다. 실무 코드는 주입받은 참조에 메서드를 호출하지 `==`로 비교하지 않는다. 프록시가 `TargetSource`를 통해 매 호출마다(또는 캐싱 후 한 번) 진짜 대상에 위임하기만 하면 계약은 충족된다 — 참조 동일성을 포기하는 대신 "생성 순서 의존성"이라는 훨씬 다루기 어려운 문제를 완전히 없앤 것이다.
- **왜 `AbstractAutoProxyCreator`는 조기 참조 시점에 프록시를 미리 만드는가**: 만약 조기 참조로 원본을 노출하고 나중에 `postProcessAfterInitialization`에서 다시 프록시로 감싸면, 이미 원본을 주입받은 다른 빈은 영원히 프록시가 아닌 원본을 들고 있게 된다 — AOP 어드바이스가 적용 안 된 참조가 컨테이너 안에 남는 것이다. 이를 막기 위해 `getEarlyBeanReference`가 "이 빈이 프록시 대상이라면, 조기 노출 시점에 이미 최종 형태(프록시)로 노출한다"고 앞당겨 결정하고, `earlyBeanReferences` 맵으로 "이미 프록시로 노출했다"는 사실을 기억해서 `postProcessAfterInitialization`에서 이중으로 감싸는 것을 막는다. 조기 참조와 최종 인스턴스의 동일성은 우연이 아니라 이 설계가 의도적으로 보장하는 것이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Lazy`는 양쪽 다 붙이는 게 아니라 **한쪽만** 붙이면 충분하고, 오히려 양쪽에 붙이면 참조 동일성 검증이 깨진다 — 프록시가 프록시를 감싸는 상황이 되기 때문이다.
- 예상대로였던 것(이번엔 실행으로 재확인): AOP 프록시가 낀 순환 참조에서 조기 참조와 최종 등록 인스턴스가 정확히 같은 프록시 객체라는 것 — 6주차에 소스로만 결론 내렸던 것을 `isSameAs`로 실제 확인했다.
- 새로 배운 것: `@Lazy` 프록시는 `TargetSource#getTarget()` 호출 시점까지 실제 조회를 미루는 별도의 프록시(`LazyDependencyTargetSource`)이며, 조기 노출 3단계 캐시와는 완전히 다른 메커니즘으로 순환을 끊는다 — 둘 다 "순환을 해결한다"는 결과는 같지만 경로는 다르다.
- Mini 구현이 보여준 것: "탐지"(project 17, `beanCreationPath`)와 "해결"(실제 Spring의 3단계 캐시 + `getEarlyBeanReference`)은 전혀 다른 난이도의 문제다 — mini가 setter 순환마저 거부하는 것은 버그가 아니라 project 17이 애초에 노린 범위 밖이다.
- 이걸로 5단계(의존성 주입, 9~10주차)가 마무리된다. 다음은 6단계, AOP로 넘어간다.
