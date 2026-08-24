# 합성 애노테이션과 `@AliasFor` — 리플렉션이 못 보는 것을 Spring은 어떻게 보는가

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`51`](../51-aspect-ordering/aspect-ordering.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. `@Service`가 왜 `@Component`처럼 스캔되는지, `@GetMapping`이 왜 `@RequestMapping(method = GET)`과 같은 효과를 내는지는 이 저장소 곳곳에서 당연하게 전제해 왔지만, 그 메커니즘("합성 애노테이션"과 `@AliasFor`) 자체를 정면으로 본 적은 한 번도 없었다 — 51개 문서를 거치는 동안 계속 전제로만 깔려 있던 기초를 이번에 직접 재현한다.

## 1. 이번 질문

- 어떤 클래스에 커스텀 애노테이션 `@Loggable`만 붙어 있고, `@Loggable` 자신은 `@Component`를 메타 애노테이션으로 달고 있다면 - 그 클래스는 평범한 `@Component` 빈처럼 컴포넌트 스캔에 걸리는가?
- 자바 표준 리플렉션(`Class#getAnnotation`)으로 그 클래스를 조회하면 `@Component`가 보이는가?
- `@AliasFor`로 커스텀 애노테이션의 속성을 메타 애노테이션의 속성에 "연결"해 두면, 그 값이 실제로 메타 애노테이션의 값으로 전달되는가 - 그리고 그게 실제 빈 해석(예: `@Qualifier` 기반 후보 선택)에도 반영되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Meta-annotations and Composed Annotations")는 `@Component`, `@Service`, `@Repository`, `@Controller` 같은 스테레오타입 애노테이션들이 서로 메타 애노테이션 관계로 연결돼 있고, 사용자도 이런 "합성 애노테이션"을 직접 만들 수 있다고 설명한다.
- `@AliasFor`의 Javadoc은 두 가지 용법을 구분한다: 같은 애노테이션 안의 "명시적 별칭"(두 속성이 항상 같은 값을 가져야 함)과, 메타 애노테이션의 속성을 "재정의"하는 용법(`annotation` 속성으로 대상 애노테이션을 지정) - `@GetMapping`이 `@RequestMapping`의 `method` 속성을 고정값 `GET`으로 재정의하는 것이 후자의 대표 예다.
- 문서는 이 메커니즘이 "제대로 동작한다"는 것만 전제하지, 그게 평범한 `getAnnotation()` 리플렉션 호출로는 절대 보이지 않는다는 것과, Spring이 그걸 보기 위해 별도의 유틸리티(`AnnotatedElementUtils`)를 쓴다는 사실은 API 문서 수준에서 명시적으로 강조하지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 자바가 애노테이션의 메타 애노테이션 관계를 어느 정도는 리플렉션으로 노출해 줄 거라 예상했다(예를 들어 `getAnnotation(Component.class)`가 `@Loggable`을 뚫고 들어가 `@Component`를 찾아 줄 거라고) — **틀렸다.** 자바 표준 리플렉션은 "직접 붙어 있는" 애노테이션만 본다 - `@Loggable`이 붙은 클래스에 `getAnnotation(Component.class)`를 호출하면 `null`이다. 메타 애노테이션 관계를 재귀적으로 뚫고 들어가는 건 전적으로 Spring이 자체 구현한 별도의 유틸리티(`AnnotatedElementUtils`)의 일이다.
- `@AliasFor`가 단순히 문서화 목적의 표시일 뿐, 실제로 값이 "전달"되는 건 아닐 거라 예상했다 — **틀렸다.** `AnnotatedElementUtils`로 메타 애노테이션을 조회하면, `@AliasFor`로 연결해 둔 속성 값이 실제로 메타 애노테이션의 해당 속성에 채워진(synthesized) 상태로 반환된다 - 커스텀 애노테이션에 기본값만 줬을 때도, 명시적으로 값을 줬을 때도 똑같이 정확히 전달됐다.
- 이 "합성"이 순전히 리플렉션 차원의 관찰일 뿐, 실제 빈 해석(`@Qualifier` 기반 자동 와이어링 후보 선택)에는 별도의 처리가 더 필요할 거라 예상했다 — **틀렸다.** `QualifierAnnotationAutowireCandidateResolver`가 애초에 내부적으로 `AnnotatedElementUtils`를 그대로 쓰고 있어서, 커스텀 `@Fast("turbo")` 하나만으로 실제 후보 빈이 정확히 좁혀졌다 - 추가 설정이 전혀 필요 없었다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/composed-annotation-lab`](../../experiments/composed-annotation-lab)

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component                              // 메타 애노테이션
public @interface Loggable {
}

@Loggable                               // @Component는 어디에도 직접 안 붙음
public class LoggableService {
}
```

```java
@Target({FIELD, PARAMETER, METHOD, TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Fast {
    @AliasFor(annotation = Qualifier.class, attribute = "value")
    String value() default "fast-lane";
}
```

```java
@Fast @Bean
public Engine defaultFastEngine() { return new Engine("fast-lane"); }

@Fast("turbo") @Bean
public Engine turboEngine() { return new Engine("turbo"); }

@Bean
public EngineConsumer engineConsumer(@Fast("turbo") Engine engine) {
    return new EngineConsumer(engine);   // turboEngine만 정확히 선택됨
}
```

```java
LoggableService.class.getAnnotation(Component.class);
// → null (평범한 리플렉션은 메타 애노테이션을 못 봄)

AnnotatedElementUtils.findMergedAnnotation(LoggableService.class, Component.class);
// → @org.springframework.stereotype.Component(value="")  (Spring은 합성해서 찾아냄)
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AnnotatedElementUtils` | 자바 표준 리플렉션 위에 "메타 애노테이션까지 재귀적으로 탐색"하는 계층을 얹은 Spring의 핵심 애노테이션 유틸리티 - `findMergedAnnotation()`이 이번 실험에서 반복해서 쓴 진입점 |
| `MergedAnnotation`/`MergedAnnotations` | `AnnotatedElementUtils`가 내부적으로 위임하는, 애노테이션 계층 전체를 모델링하는 더 낮은 레벨의 타입 - `@AliasFor` 속성 전달(synthesis)이 실제로 계산되는 곳 |
| `@AliasFor` | `annotation` 속성을 지정하면 "메타 애노테이션 속성 재정의", 지정하지 않으면 "같은 애노테이션 안의 명시적 별칭"이라는 두 가지 용법을 하나의 애노테이션으로 표현 |
| `AnnotationTypeFilter` | 컴포넌트 스캔이 후보 클래스를 걸러낼 때 쓰는 필터 - 생성자의 `considerMetaAnnotations`(스캔에서는 기본 `true`)가 켜져 있으면 `metadata.hasMetaAnnotation(...)`까지 확인 |
| `QualifierAnnotationAutowireCandidateResolver` | `@Qualifier` 기반 후보 선택 로직 - 후보의 애노테이션들을 순회하며 `AnnotatedElementUtils`로 메타 애노테이션까지 확인하므로, 커스텀 합성 `@Qualifier` 애노테이션도 별도 설정 없이 그대로 동작 |

## 6. 호출 흐름

```text
[컴포넌트 스캔]
ClassPathScanningCandidateComponentProvider가 LoggableService를 후보로 발견
  → AnnotationTypeFilter(Component.class, considerMetaAnnotations=true).match(metadata)
    → metadata.hasAnnotation("...Component") → false (직접 붙은 애노테이션 아님)
    → metadata.hasMetaAnnotation("...Component") → true (@Loggable을 통해 메타로 붙어 있음)
  → 컴포넌트로 채택 → "loggableService"라는 이름으로 빈 등록

[리플렉션 대 AnnotatedElementUtils]
LoggableService.class.getAnnotation(Component.class)
  → JDK 리플렉션은 클래스에 "직접" 붙은 애노테이션 배열만 봄 → @Loggable만 있음 → null

AnnotatedElementUtils.findMergedAnnotation(LoggableService.class, Component.class)
  → 직접 붙은 애노테이션들(@Loggable)을 순회
  → 각각의 메타 애노테이션까지 재귀 탐색 → @Loggable 위에서 @Component 발견
  → @Component의 속성들을 합성(synthesize)한 프록시 인스턴스를 반환

[@AliasFor를 통한 속성 전달]
CarConfig#turboEngine 메서드에서 AnnotatedElementUtils.findMergedAnnotation(method, Qualifier.class)
  → 메서드에 직접 붙은 @Fast("turbo") 발견
  → @Fast의 value 속성이 @AliasFor(annotation=Qualifier.class, attribute="value")로 선언돼 있음을 확인
  → 합성될 @Qualifier 인스턴스의 value 속성 값으로 "turbo"를 채워 넣음
  → 반환된 @Qualifier(value="turbo")는 실제로 @Qualifier("turbo")를 직접 쓴 것과 구분 불가능

[실제 자동 와이어링]
engineConsumer(@Fast("turbo") Engine engine) 파라미터 해석
  → QualifierAnnotationAutowireCandidateResolver#checkQualifiers
    → 파라미터의 @Fast를 발견 → isQualifier(Fast.class) → true(@Qualifier가 메타로 붙어 있음)
    → checkQualifier() → AnnotatedElementUtils로 각 후보 빈(defaultFastEngine, turboEngine)의
      합성된 @Qualifier 값과 요구값("turbo")을 비교
    → turboEngine만 일치 → 그 빈으로 확정
```

평범한 리플렉션이 보는 것과 `AnnotatedElementUtils`가 보는 것의 차이, 그리고 `@AliasFor`가 값을 실제로 실어 나르는 지점을 함께 그린 다이어그램: [`diagrams/composed-annotation-flow.md`](diagrams/composed-annotation-flow.md)

## 7. 브레이크포인트

이번 주제도 25~51번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 8번 절의 실행 결과(리플렉션과 `AnnotatedElementUtils`가 정확히 갈리는 지점, `@AliasFor` 값이 정확히 전달되는 지점)가 이미 충분히 구체적인 증거였다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 확인했다:

```text
org.springframework.core.type.filter.AnnotationTypeFilter#matchSelf
org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver#checkQualifiers
org.springframework.core.annotation.AnnotatedElementUtils#findMergedAnnotation
```

## 8. 런타임 관찰

[`ComposedAnnotationTest`](../../experiments/composed-annotation-lab/src/test/java/lab/experiments/composedannotation/ComposedAnnotationTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `LoggableService.class.getAnnotation(Component.class)` (평범한 리플렉션) | `null` |
| `AnnotatedElementUtils.findMergedAnnotation(LoggableService.class, Component.class)` | `@Component(value="")` (합성됨) |
| `@Loggable`만 붙은 클래스가 컴포넌트 스캔에 걸리는가 | `containsBean("loggableService")` → `true` |
| `defaultFastEngine`(값 미지정) / `turboEngine`(값 `"turbo"`) 메서드의 합성 `@Qualifier` | 각각 `value="fast-lane"`(기본값 전달) / `value="turbo"`(명시값 전달) |
| `@Fast("turbo")`로 자동 와이어링한 `EngineConsumer` | `turboEngine`이 정확히 선택됨(`engine().label() == "turbo"`) |

**직접 겪은 것**: 예상했던 결과가 스파이크 단계에서 한 치의 오차 없이 그대로 재현돼서, 오히려 "뭔가 놓친 게 있지 않을까" 하고 다시 소스를 확인했다 - `QualifierAnnotationAutowireCandidateResolver`가 커스텀 합성 `@Qualifier`를 처리하기 위해 별도의 특별한 코드를 갖고 있을 거라 짐작했는데, 실제로는 그냥 처음부터 `AnnotatedElementUtils`로 모든 애노테이션을 조회하고 있어서 "커스텀 합성 애노테이션 지원"이 별도 기능이 아니라 애초의 구현 방식 자체에서 공짜로 따라오는 결과였다는 걸 소스를 다시 읽고 나서야 확인했다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, `AnnotationTypeFilter` 생성자의 `considerMetaAnnotations` 매개변수 이름 자체와, `AnnotatedElementUtils.findMergedAnnotation()`이 실제로 반환하는 값(합성된 `@Component`/`@Qualifier` 인스턴스, `toString()`으로 찍어 보면 진짜 애노테이션과 구분이 안 된다)이 이미 충분히 구체적인 증거였다 - 46~51번 문서가 예외 메시지·소스 주석·클래스 Javadoc 하나로 설계 의도를 증명했던 것과 같은 정신이 이번에도 반복됐다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `MergedAnnotations`가 실제로 하는 일(애노테이션 트리를 재귀 탐색하고, `@AliasFor` 관계를 미리 인덱싱해 둔 뒤 다이나믹 프록시로 속성 값을 합성하는 것)은 리플렉션 API 위에 상당히 복잡한 계층을 쌓은 것이라, 이번 주의 핵심 질문(합성이 실제로 일어나는가, 그 값이 실제 로직에 반영되는가)에 비해 축소 구현으로 재현하기엔 지나치게 큰 부담이었다 - 이 저장소가 46번(제네릭 타입 비교)에서 이미 같은 이유로 축소 구현을 생략했던 것과 같은 판단이다.

## 11. Spring 설계 의도

- **왜 자바 표준 리플렉션을 그대로 쓰지 않고 별도의 유틸리티를 만들었는가**: 자바 언어 자체는 애노테이션 사이의 "의미적" 관계(이 애노테이션이 저 애노테이션의 특수화다)를 전혀 모른다 - 애노테이션에 다른 애노테이션을 붙이는 것(메타 애노테이션)은 문법적으로는 가능하지만, 언어 차원에서는 아무 의미도 부여받지 못한다. Spring이 스테레오타입 계층(`@Service`→`@Component`)이나 합성 웹 애노테이션(`@GetMapping`→`@RequestMapping`) 같은 기능을 제공하려면, 이 "의미"를 스스로 정의하고 스스로 탐색하는 계층을 자바 언어 위에 별도로 쌓을 수밖에 없었다 - `AnnotatedElementUtils`/`MergedAnnotations`는 그 계층이다.
- **왜 `@AliasFor`는 단순 매핑 테이블이 아니라 애노테이션 자기 자신에 선언하는 방식을 택했는가**: 매핑 관계를 외부의 별도 설정(XML이나 프로퍼티 파일 같은)으로 관리했다면, 애노테이션 정의와 그 별칭 관계가 물리적으로 분리되어 유지보수 중 어긋나기 쉬웠을 것이다. `@AliasFor`를 속성 자체에 선언하게 만든 것은, "이 속성이 무엇을 대신하는가"라는 정보를 그 속성과 같은 곳에 두어 항상 함께 움직이게 만들기 위한 선택으로 보인다 - 게다가 컴파일 타임에 애노테이션 프로세서(`@AliasFor` 자체 검증 로직)로 잘못된 별칭 선언(타입 불일치, 기본값 불일치 등)을 조기에 잡아낼 수 있다는 부수적 이점도 있다.
- **왜 `QualifierAnnotationAutowireCandidateResolver`는 커스텀 `@Qualifier` 지원을 위해 특별한 코드를 추가하지 않았는가**: 애초에 이 리졸버가 애노테이션을 조회하는 유일한 경로가 `AnnotatedElementUtils`이기 때문에, "합성 애노테이션도 지원한다"는 기능이 별도로 설계된 게 아니라 **일관되게 같은 조회 방식을 썼기 때문에 자연스럽게 따라온 결과**다. 이 저장소가 반복해서 확인해 온 "특별한 경우를 위한 특별한 코드를 추가하지 않고, 하나의 일관된 메커니즘이 여러 경우를 자연스럽게 커버하게 만든다"는 설계 철학이, 이번엔 "합성 애노테이션 지원"이라는 겉보기엔 커 보이는 기능이 실제로는 "어디서나 `AnnotatedElementUtils`를 쓴다"는 단순한 일관성의 부산물이었다는 형태로 나타났다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 애초에 "이건 자바 리플렉션이 어느 정도 도와줄 것"이라는 전제 자체가 틀렸다는 것 - 메타 애노테이션 관계를 뚫고 들어가는 일은 전적으로, 100% Spring이 스스로 구현한 것이고, JDK는 정말 딱 "직접 붙은 것"만 본다. `getAnnotation()`이 `null`을 반환하는 걸 직접 보고 나서야 이 경계가 얼마나 뚜렷한지 실감했다.
- 예상 밖이었던 것: `@Qualifier` 기반 자동 와이어링이 커스텀 합성 애노테이션을 지원하기 위해 별도의 코드를 갖고 있지 않다는 것 - "커스텀 애노테이션도 되나?"라는 질문에 대한 답이 "네, 특별히 지원해서요"가 아니라 "네, 애초에 다르게 만들 방법이 없어서 자연스럽게 됩니다"였다는 게 이번 발견 중 가장 인상 깊었다.
- 예상대로였던 것: `@AliasFor`가 실제로 값을 실어 나른다는 것 - 기본값과 명시적 값 둘 다 정확히 전달되는 걸 보면서, 이게 단순한 문서화용 표시가 아니라 진짜 런타임 동작이라는 걸 확인했다.
- 새로 배운 것: 51개 문서 동안 계속 당연하게 전제해 왔던 것(`@Service`가 왜 스캔되는지, `@GetMapping`이 왜 `@RequestMapping`처럼 동작하는지)이 사실은 이 저장소가 아직 한 번도 정면으로 다루지 않은 기초 메커니즘이었다는 것 - 46~51번처럼 점점 더 좁고 구체적인 지점을 파고드는 것만이 심화가 아니라, 이렇게 "계속 당연시해 온 전제 자체"로 되돌아가 확인하는 것도 똑같이 가치 있는 심화라는 걸 이번에 다시 확인했다.
