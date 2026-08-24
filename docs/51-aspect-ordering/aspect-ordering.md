# 여러 Aspect의 실행 순서 — 정렬이 아니라 "양파 껍질"

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`50`](../50-placeholder-resolution/placeholder-resolution.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`12-auto-proxy-creator`](../12-auto-proxy-creator/auto-proxy-creator.md) 문서는 스스로 이 빈틈을 명시적으로 밝혀 뒀다: "정렬(`sortAdvisors`)이나 `@Order` 기반 우선순위는 다루지 않았다 — Advisor가 하나뿐이라 필요하지 않았다." 이번엔 그 빈틈을 채운다 - 여러 개의 `@Around` 어드바이스가 같은 메서드를 감쌀 때, `@Order`(또는 `Ordered`)가 실제로 무엇을 결정하는지 직접 재현한다.

## 1. 이번 질문

- 여러 `@Aspect`가 같은 메서드에 적용될 때, 최종 실행 순서는 단순히 order 값으로 "정렬"된 순서인가?
- order 값이 가장 작은 어드바이스가 "가장 먼저 실행되고 가장 먼저 끝나는" 순서인가, 아니면 다른 규칙인가?
- 이 순서는 `@Bean` 메서드가 선언된(등록된) 순서와 무관하게, 순전히 order 값만으로 결정되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Advice Ordering")는 같은 조인 포인트에 여러 어드바이스가 적용될 때 실행 순서를 `@Order`(또는 `Ordered`)로 제어할 수 있다고 설명하고, "가장 높은 우선순위(order 값이 가장 작음)의 어드바이스가 진입 시(entry) 가장 먼저 실행되고, 반환 시(return) 가장 나중에 실행된다"고 서술한다.
- 문서는 이 규칙을 "동심원"(concentric circles)에 비유해 설명하지만, 이게 정확히 "정렬된 리스트를 순서대로 실행"과 어떻게 다른지, 그리고 그 차이가 `@Around` 어드바이스의 실제 실행 로그에서 어떻게 관찰되는지는 API 문서 수준에서 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- order 값이 작은 어드바이스일수록 먼저 실행되고 먼저 끝날 거라 예상했다(단순 정렬된 리스트를 순서대로 도는 것처럼) — **틀렸다.** 실제로는 "먼저 시작하면 가장 나중에 끝난다"는, 리스트 순회가 아니라 **중첩(nesting)** 구조다 - order 값이 가장 작은 어드바이스가 가장 바깥쪽 껍질이 되어, 진입은 가장 먼저 하지만 빠져나오는 건 가장 나중이다.
- `@Bean` 메서드의 선언 순서가 조금이라도 영향을 줄 거라 예상했다(46~48번에서 반복해서 확인한 "순서가 결과에 영향을 준다"는 패턴 때문에) — **틀렸다.** `AbstractAdvisorAutoProxyCreator#sortAdvisors`가 후보 Advisor 목록을 order 값 기준으로 명시적으로 재정렬하므로, `@Bean` 메서드를 order 값과 완전히 어긋나는 순서로 선언해도 최종 실행 순서는 오직 order 값에만 좌우된다 - 48번(BPP 등록 순서)의 "그룹은 순서를 무시하지 않지만, 그 그룹 안 나머지 버킷은 값을 무시한다"는 발견과 정확히 대칭되는, "이번엔 값이 등록 순서를 완전히 이긴다"는 반대 사례였다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/aspect-ordering-lab`](../../experiments/aspect-ordering-lab)

```java
@Aspect
public class OrderedLoggingAspect implements Ordered {
    private final String label;
    private final int order;
    private final List<String> log;
    // ... 생성자 생략

    @Around("execution(* lab.experiments.aspectordering.Greeter.greet(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        log.add(label + "-before");
        try {
            return joinPoint.proceed();
        } finally {
            log.add(label + "-after");
        }
    }

    @Override
    public int getOrder() { return order; }
}
```

```java
@Configuration
@EnableAspectJAutoProxy
public class AspectOrderingConfig {
    // 일부러 order 값(A=1, B=2, C=3)과 어긋나는 순서(C, A, B)로 선언
    @Bean public OrderedLoggingAspect aspectC(List<String> log) { return new OrderedLoggingAspect("C", 3, log); }
    @Bean public OrderedLoggingAspect aspectA(List<String> log) { return new OrderedLoggingAspect("A", 1, log); }
    @Bean public OrderedLoggingAspect aspectB(List<String> log) { return new OrderedLoggingAspect("B", 2, log); }
}
```

```java
greeter.greet();
// log == [A-before, B-before, C-before, C-after, B-after, A-after]
//         └─────────────── order 값(1<2<3) 순서 ──────────────┘
//         가장 바깥(A) → 그 안(B) → 가장 안쪽(C) → 다시 C → B → A 순으로 "빠져나온다"
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AbstractAdvisorAutoProxyCreator#findEligibleAdvisors` | 대상 빈에 적용 가능한 Advisor 후보를 모으고, 하나보다 많으면 `sortAdvisors()`를 호출 |
| `AspectJAwareAdvisorAutoProxyCreator#sortAdvisors` | 단순 `Comparator` 정렬이 아니라 AspectJ의 `PartialOrder` 유틸리티로 정렬 - `@EnableAspectJAutoProxy`가 등록하는 `AnnotationAwareAspectJAutoProxyCreator`가 바로 이 클래스를 상속 |
| `AspectJPrecedenceComparator` | "서로 다른 aspect에 속한 두 어드바이스라면, order 값이 더 작은 aspect의 어드바이스가 더 높은 우선순위(=먼저 실행)"라는 규칙을 구현 - 내부적으로 `AnnotationAwareOrderComparator`(48번 문서에서 이미 본 것과 같은 클래스)를 그대로 재사용 |
| 프록시의 어드바이스 체인(`ReflectiveMethodInvocation`) | 정렬된 순서 그대로 인터셉터 체인을 만들고, 각 `@Around` 어드바이스의 `proceed()` 호출이 "다음 어드바이스(또는 최종 타깃)를 부르고 돌아온다"는 재귀 구조이기 때문에 자연스럽게 중첩(양파 껍질)이 만들어짐 |

## 6. 호출 흐름

```text
AnnotationAwareAspectJAutoProxyCreator#postProcessAfterInitialization(greeter)
  → findEligibleAdvisors(GreeterImpl.class, "greeter")
    → findAdvisorsThatCanApply() → 포인트컷(execution(* ...greet(..)))에 매칭되는
      세 어드바이스(A, B, C) 전부 후보로 채택
    → sortAdvisors(candidateAdvisors)
      → PartialOrder.sort(...) ← AspectJPrecedenceComparator로 비교
        → "다른 aspect에 속한 두 어드바이스는 order 값이 작을수록 높은 우선순위"
        → 결과: [A(order=1), B(order=2), C(order=3)]  ← @Bean 선언 순서(C,A,B)와 무관
  → 정렬된 순서 그대로 프록시의 어드바이스 체인 구성

greeter.greet() 호출 (프록시를 거침)
  → ReflectiveMethodInvocation이 체인을 순서대로 재귀 호출
    A.around(joinPoint) 시작
      log.add("A-before")
      joinPoint.proceed() 호출  ← 체인의 다음 어드바이스로
        B.around(joinPoint) 시작
          log.add("B-before")
          joinPoint.proceed() 호출
            C.around(joinPoint) 시작
              log.add("C-before")
              joinPoint.proceed() 호출 → 진짜 GreeterImpl#greet() 실행 → "hello" 반환
              log.add("C-after")
            C.around 종료, "hello" 반환
          log.add("B-after")
        B.around 종료, "hello" 반환
      log.add("A-after")
    A.around 종료, "hello" 반환
```

"정렬된 리스트를 순회"하는 게 아니라 "재귀 호출이 만드는 자연스러운 중첩"이라는 것, 그리고 그 중첩의 방향이 order 값으로 결정된다는 것을 함께 그린 다이어그램: [`diagrams/aspect-ordering-flow.md`](diagrams/aspect-ordering-flow.md)

## 7. 브레이크포인트

이번 주제도 25~50번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 8번 절의 실행 결과(정확한 순서로 기록된 로그)가 이미 충분히 구체적인 증거였다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 확인했다:

```text
org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator#findEligibleAdvisors
org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator#sortAdvisors
org.springframework.aop.aspectj.autoproxy.AspectJPrecedenceComparator#compare
```

`AspectJPrecedenceComparator`의 클래스 Javadoc에 있는 한 문장이 특히 명확했다: "Orders AspectJ advice/advisors by precedence (*not* invocation order)." - "우선순위"와 "호출 순서"가 다른 개념이라는 걸 클래스 설명 자체가 미리 경고해 두고 있었다.

## 8. 런타임 관찰

[`AspectOrderingTest`](../../experiments/aspect-ordering-lab/src/test/java/lab/experiments/aspectordering/AspectOrderingTest.java) (2개):

| 시나리오 | order 값 | `@Bean` 선언 순서 | 실행 로그 |
| --- | --- | --- | --- |
| 정방향 | A=1, B=2, C=3 | C → A → B | `A-before, B-before, C-before, C-after, B-after, A-after` |
| 역방향(같은 세 어드바이스, order 값만 뒤집음) | A=3, B=2, C=1 | A → B → C | `C-before, B-before, A-before, A-after, B-after, C-after` |

**직접 겪은 것**: 스파이크 테스트를 돌리자 예상했던 "양파 껍질" 순서가 정확히 재현됐다 - 특히 `@Bean` 메서드를 order 값과 일부러 어긋나게(C, A, B) 선언했는데도 실행 순서가 order 값(1, 2, 3)만 정확히 따랐다는 것이, `sortAdvisors()`가 실제로 등록 순서를 완전히 무시하고 값만 본다는 걸 가장 명확하게 보여줬다. 48번(BPP 등록 순서) 문서에서 "나머지 그룹은 정렬 자체를 안 해서 값을 무시한다"는 정반대 사례를 이미 확인해 둔 덕분에, 이번엔 "언제 값이 이기고 언제 등록 순서가 이기는가"를 대조해서 볼 수 있는 눈이 이미 갖춰져 있었다 - 새 실험을 설계할 때마다 이전 실험에서 얻은 대조군이 점점 쌓이고 있다는 걸 체감했다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, `AspectJPrecedenceComparator` 클래스 자체의 Javadoc(정확한 규칙 서술: "서로 다른 aspect에 속한 두 어드바이스라면 order 값이 작은 쪽이 더 높은 우선순위")과, `AspectJAwareAdvisorAutoProxyCreator#sortAdvisors` 메서드 바로 위 주석("On the way in to a join point, the highest precedence advisor should run first. On the way out of a join point, the highest precedence advisor should run last.")이 이미 충분히 구체적인 명세 역할을 했다 - 46~50번 문서가 예외 메시지·소스 주석 하나로 설계 의도를 증명했던 것과 같은 정신이 이번에도 반복됐다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. 11·12주차의 `mini-aop`/`mini-auto-proxy`가 이미 다룬 "인터셉터 체인 + `proceed()`의 재귀 호출"이라는 골격 위에서, 여러 어드바이스의 실행 순서는 사실 새로운 메커니즘이 아니라 "체인에 어떤 순서로 어드바이스를 넣어 두었는가"라는 한 가지 변수의 결과일 뿐이다 - 이번 주의 가치는 그 골격에 새 기능을 얹는 게 아니라, "그 순서를 결정하는 규칙"(order 값에 의한 사전 정렬) 자체를 확인하는 데 있었다.

## 11. Spring 설계 의도

- **왜 "정렬된 순서로 순회"가 아니라 "중첩" 구조를 택했는가**: `@Around` 어드바이스는 본질적으로 대상 메서드 호출을 감싸는 함수다 - "먼저 실행되는 것이 나중에 끝난다"는 중첩 구조는 `@Around`가 `proceed()`를 호출해서 다음 단계로 넘어가는 재귀적 설계의 **자연스러운 결과**이지, 별도로 설계해서 만든 특별한 규칙이 아니다. 만약 정말 "정렬된 리스트를 순서대로 실행"하고 싶었다면 애초에 `@Around` 대신 `@Before`/`@After`처럼 감싸지 않는 단일 단계 어드바이스만 있었을 것이다 - 중첩은 `@Around`가 "메서드 호출을 감싼다"는 정의 자체에서 필연적으로 나온다.
- **왜 order 값이 등록/선언 순서를 완전히 이기는가**: AOP의 어드바이스 적용 순서는 트랜잭션 경계·보안 검사·로깅처럼 서로 다른 관심사가 정확한 상대적 순서로 겹겹이 쌓여야 의미가 있는 경우가 많다(예: 보안 검사가 트랜잭션보다 바깥에 있어야 인증 실패 시 트랜잭션 자체를 시작하지 않을 수 있다). 이런 순서 보장이 "어쩌다 어떤 순서로 빈이 등록됐는가"라는, 개발자가 늘 통제하기 쉽지 않은 우연에 좌우된다면 위험하다 - `sortAdvisors()`가 등록 순서를 완전히 무시하고 명시적 order 값만 신뢰하는 것은, 관심사의 겹치는 순서를 "명시적으로 선언된 계약"으로 만들어서 등록 순서라는 우연에서 완전히 독립시키기 위한 설계로 읽힌다.
- **왜 `AspectJPrecedenceComparator`는 "invocation order"가 아니라 "precedence"라는 용어를 쓰는가**: 이 저장소가 반복해서 확인해 온 "이름이 곧 설계 의도를 드러낸다"는 패턴이 여기서도 나타난다 - "invocation order"라고 이름 붙였다면 사람들은 이걸 "실행되는 순서 그대로"라고 오해하기 쉬웠을 것이다(정확히 이번 문서 3번 절에서 필자가 처음에 했던 오해다). "precedence"(우선순위)라는 이름은, 이게 "누가 더 바깥쪽 권한을 갖는가"라는 계층적 개념이지 "누가 몇 번째로 뛰는가"라는 순차적 개념이 아니라는 걸 이름 자체로 미리 경고한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: "order 값이 작으면 먼저 끝난다"는 직관이 정확히 반대였다는 것 - order 값이 가장 작은 어드바이스는 가장 먼저 시작하지만 가장 나중에 끝난다. "정렬된 리스트를 순서대로 실행"이라는 익숙한 멘탈 모델이, `@Around`라는 "감싸는" 어드바이스 앞에서는 통하지 않는다는 걸 직접 로그로 보고 나서야 확실히 교정됐다.
- 예상 밖이었던 것: `AspectJPrecedenceComparator`가 클래스 Javadoc 첫 줄에서부터 "이건 실행 순서가 아니라 우선순위를 정하는 것"이라고 스스로 경고하고 있었다는 것 - 46~50번에서 반복해서 본 "Spring 소스 자체가 흔한 오해를 예상하고 미리 답을 남겨 둔다"는 패턴이 이번엔 클래스 이름과 Javadoc 첫 문장이라는 형태로 나타났다.
- 예상대로였던 것: order 값이 등록 순서를 완전히 이긴다는 것 - 다만 이게 48번(BPP 등록 순서)에서 본 "나머지 버킷은 값을 무시한다"는 사례와 정확히 대칭되는, "이번엔 값이 이긴다"는 반대 결과라는 걸 명시적으로 대조해 보고 나서야, "정렬 여부는 매번 그 컴포넌트의 설계에 달려 있다"는 걸 두 사례를 나란히 놓고 확인하게 됐다.
- 새로 배운 것: 여러 심화 주제를 거치면서, 새 실험을 설계할 때 이전 주제에서 확인한 대조 사례("이건 값을 본다" vs "이건 값을 무시한다", "이건 즉시 실패한다" vs "이건 나중에 실패한다")를 직접 재료로 쓸 수 있게 됐다는 것 - 48번의 발견이 이번 실험(플레이스홀더 검증 순서)뿐 아니라 이번 문서(값 vs 등록 순서의 대조군)에도 그대로 이어져 쓰였다.
