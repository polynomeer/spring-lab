# 생성자 주입과 의존성 탐색 — 두 단계로 나뉜 결정

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 9주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 14(Dependency Resolution Matrix)·프로젝트 15(Mini Constructor Injector)에 대응하는 분석 문서다.

## 1. 이번 질문

생성자가 여러 개이면 어떤 생성자를 선택하는가? `@Autowired`가 없어도 단일 생성자가 주입되는 이유는 무엇인가? 주입 후보는 어떻게 검색하는가? 타입이 같은 빈이 여러 개이면 어떻게 처리하는가?

이 질문들은 사실 **서로 다른 두 단계**에 대한 질문이다 — "어떤 생성자를 쓸 것인가"(`AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors`)와 "그 생성자의 파라미터 하나를 무엇으로 채울 것인가"(`DefaultListableBeanFactory#determineAutowireCandidate`)는 완전히 다른 코드다. 이 구분이 이번 문서의 핵심 구조다.

## 2. 공식 문서 요약

- `AutowiredAnnotationBeanPostProcessor`가 `SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors`를 구현해서 "이 클래스는 어떤 생성자(들)로 만들 수 있는가"를 결정한다.
- `ConstructorResolver`가 실제로 생성자를 호출하고, 각 파라미터를 `DependencyDescriptor`로 감싸서 `DefaultListableBeanFactory#resolveDependency`/`doResolveDependency`에 위임한다.
- 후보가 여러 개일 때의 우선순위는 `determineAutowireCandidate`에 있다 — 6번에서 소스로 정확한 순서를 확인했다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 생성자가 여러 개이고 `@Autowired`가 하나도 없으면, Spring이 "그나마 파라미터가 가장 많이 매칭되는" 생성자를 나름의 휴리스틱으로 골라줄 것이라 예상했다 — **완전히 틀렸다.** 실제로는 후보 자체를 정하지 못하고 기본 생성자로 떨어지며, 등록된 빈이 있어도 무시한다.
- `@Qualifier`가 이름 기반 매칭보다 우선순위가 높을 것이라 예상했다 — 실제로는 파라미터/필드의 순수 이름이 `@Qualifier`가 제안하는 이름보다 **먼저** 검사된다(6번 참고). 실무에서 거의 부딪히지 않는 차이지만 예상과는 달랐다.
- `Optional`/`List`/`ObjectProvider`가 "실패를 관용한다"는 것은 알고 있었지만, `ObjectProvider`가 생성자 주입 시점이 아니라 **호출 시점**(`getObject()`/`getIfAvailable()`)에 해석을 미룬다는 것은 실제로 실행해서 확인하기 전까지 명확하지 않았다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/dependency-resolution-matrix`](../../experiments/dependency-resolution-matrix)
```java
public class MultiConstructorNoAutowiredBean {
    public MultiConstructorNoAutowiredBean() { this.dependency = null; }
    public MultiConstructorNoAutowiredBean(Dependency dependency) { this.dependency = dependency; }
}
```
```java
context.registerBean(Dependency.class);
context.registerBean(MultiConstructorNoAutowiredBean.class);
context.refresh();
// Dependency가 등록돼 있어도 bean.getDependency() == null
```

**축소 구현** — [`mini-spring/mini-container`](../../mini-spring/mini-container)
```java
Constructor<?> selectConstructor(String name, Class<?> beanClass) {
    Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
    if (constructors.length == 1) return constructors[0];
    // ... @MiniAutowired 우선, 없으면 기본 생성자, 그마저 없으면 AmbiguousConstructorException
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AutowiredAnnotationBeanPostProcessor` | "어떤 생성자를 쓸 것인가"를 결정 (`determineCandidateConstructors`) |
| `ConstructorResolver` | 결정된 생성자를 실제로 호출하고 인자를 채움 |
| `DependencyDescriptor` | 주입 지점(파라미터/필드) 하나를 감싸는 메타데이터 — 타입, 이름, required 여부 |
| `DefaultListableBeanFactory#resolveDependency` | "이 주입 지점을 무엇으로 채울 것인가" 전체 흐름의 진입점 |
| `DefaultListableBeanFactory#determineAutowireCandidate` | 후보가 여러 개일 때 하나로 좁히는 우선순위 체인 |
| (mini) `SimpleBeanFactory#selectConstructor` | 생성자 선택 (실제 Spring의 `determineCandidateConstructors`에 대응) |
| (mini) `SimpleBeanFactory#resolveArgument` | 파라미터 하나 해석 (실제 Spring의 `resolveDependency`에 대응) |

## 6. 호출 흐름

두 결정 트리: [`diagrams/resolution-decision-trees.md`](diagrams/resolution-decision-trees.md)

```text
[1단계: 생성자 선택]
determineCandidateConstructors(beanClass)
  생성자 1개               → 그 생성자 (애노테이션 불필요)
  @Autowired(required=true) 2개 이상 → BeanCreationException (시작 시점)
  @Autowired 정확히 1개     → 그 생성자 (+ required=false면 기본 생성자를 폴백으로 추가)
  @Autowired 0개, 기본 생성자 있음 → 기본 생성자 (다른 생성자는 완전히 무시)
  @Autowired 0개, 기본 생성자 없음 → 후보 없음 (null)

[2단계: 파라미터 하나 해석] - determineAutowireCandidate(candidates, descriptor)
  Step 1: @Primary 후보가 정확히 1개인가?
  Step 2a: 후보 빈 이름이 파라미터/필드의 순수 이름과 일치하는가?
  Step 2b: 후보 빈 이름이 @Qualifier가 제안하는 이름과 일치하는가?
  Step 3: (우리 실험 밖) @Order/@Priority 기준 최고 우선순위 후보
  이 중 하나도 못 정하면 → NoUniqueBeanDefinitionException → UnsatisfiedDependencyException으로 래핑
```

(mini) `SimpleBeanFactory`는 1단계를 `selectConstructor()`로, 2단계를 `resolveArgument()`(안에서 `getBean(Class)` 호출, `@MiniQualifier`가 있으면 `getBean(String)`으로 이름 직접 조회)로 구현했다 — Step 3(우선순위 기반)은 생략했다.

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 다음은 추적 후보다.

```text
org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors
org.springframework.beans.factory.support.ConstructorResolver#autowireConstructor
org.springframework.beans.factory.support.DefaultListableBeanFactory#doResolveDependency
org.springframework.beans.factory.support.DefaultListableBeanFactory#determineAutowireCandidate
```

## 8. 런타임 관찰

[`ConstructorSelectionTest`](../../experiments/dependency-resolution-matrix/src/test/java/lab/experiments/depres/ConstructorSelectionTest.java) (6개) + [`CandidateSelectionTest`](../../experiments/dependency-resolution-matrix/src/test/java/lab/experiments/depres/CandidateSelectionTest.java) (6개) + [`InjectionFormTest`](../../experiments/dependency-resolution-matrix/src/test/java/lab/experiments/depres/InjectionFormTest.java) (6개)로 고정한 18개 결과를 요약하면:

| 조건 | 결과 |
| --- | --- |
| 생성자 1개 | 자동 주입 |
| 생성자 여럿 + `@Autowired` 1개 | 그 생성자 주입 |
| 생성자 여럿 + `@Autowired` 2개(required) | `BeanCreationException` (시작 시점) |
| `@Autowired(required=false)` + 의존성 없음 | 기본 생성자로 폴백 |
| 생성자 여럿 + `@Autowired` 0개 | **기본 생성자, 등록된 빈이 있어도 무시** |
| 후보 0개 (필수 타입) | `UnsatisfiedDependencyException` |
| 후보 2개, `@Primary`/`@Qualifier`/이름 일치 없음 | `UnsatisfiedDependencyException` |
| 후보 2개 + `@Primary` | Primary 선택 |
| 후보 2개 + `@Qualifier` | Qualifier 지정 빈 선택 |
| 후보 2개 + 파라미터 이름이 빈 이름과 일치 | 이름 일치 빈 선택 (애노테이션 불필요) |
| `Optional<T>`, 후보 없음 | `Optional.empty()` |
| `List<T>`, 후보 없음 | 빈 리스트 (에러 아님) |
| `ObjectProvider<T>`, 후보 없음 | 생성자 주입 자체는 성공, `getIfAvailable()`이 `null` |

[`mini-container`의 생성자 주입 테스트](../../mini-spring/mini-container/src/test/java/lab/minispring/container/SimpleBeanFactoryConstructorInjectionTest.java)(9개)도 위 표의 핵심 행들과 동일한 결과를 재현했다. 부수적으로 확인한 것: 생성자 파라미터로 순환 참조를 만들면(`CircularA(CircularB)` ↔ `CircularB(CircularA)`) `beanCreationPath` 재진입 감지가 그대로 걸린다 — project 15 이전에는 정적 필드로 순환을 흉내내야 했지만, 이제는 실제 생성자 주입 경로에서 자연스럽게 재현된다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `DefaultListableBeanFactoryTests`로 확인했다.

- **`autowireWithTwoMatchesForConstructorDependency()`**: 같은 타입의 빈 두 개(`rod`, `rod2`)를 등록하고 생성자 자동와이어링을 시도하면 `UnsatisfiedDependencyException`이 발생하고, 메시지에 두 후보 이름이 모두 포함된다 — 우리 `multipleCandidatesWithoutDisambiguationThrows` 테스트와 정확히 같은 시나리오다.
- **`autowireWithUnsatisfiedConstructorDependency()`**: 필요한 타입의 빈이 아예 없으면 마찬가지로 `UnsatisfiedDependencyException` — 우리 `noCandidateThrowsUnsatisfiedDependencyException`과 대응.
- **`autowireWithSatisfiedConstructorDependency()`**: 후보가 정확히 하나면 `getBean("rod")`으로 조회한 것과 **동일 인스턴스**가 주입된다는 것을 `isSameAs`로 검증 — 우리도 `singleCandidateIsInjectedDirectly`에서 같은 방식으로 확인했다.

`AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors`(4번에서 이미 코드를 인용) 자체를 직접 검증하는 이름의 공식 테스트는 찾지 못했다 — 이 메서드의 동작은 여러 통합 테스트에 걸쳐 간접적으로만 검증되는 것으로 보인다(정직하게 밝혀 둔다).

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-container`(project 15) — 별도 모듈 대신 기존 컨테이너를 확장했다(project 7·13과 같은 이유: 생성자 선택은 핵심 `BeanFactory` 동작이라 분리할 이유가 없다).

**구현한 것**
- `selectConstructor()`: 단일 생성자 자동 선택, `@MiniAutowired` 선택, 두 개 이상이면 `AmbiguousConstructorException`, 애노테이션 없이 여럿이면 기본 생성자 폴백(없으면 예외) — 실제 Spring의 다섯 가지 분기(6번)를 전부 재현
- `resolveArgument()`: `@MiniQualifier`가 있으면 이름으로, 없으면 타입으로 해석
- `BeanDefinition.primary` + `asPrimary()`: `getBean(Class)`가 후보 여럿일 때 primary 하나를 우선 선택하도록 확장

**생략한 것**
- **이름 기반 tiebreak** — 실제 Spring의 Step 2a(파라미터 이름이 빈 이름과 일치)에 해당하는 규칙이 없다. 지금은 `@Primary`가 없으면 바로 `NoUniqueBeanException`이다.
- **`Optional<T>`/`List<T>`/`ObjectProvider<T>` 형태의 주입** (카탈로그 4단계) — `getBean(Class)`가 여전히 "정확히 하나 아니면 예외"만 알고, "감싸서 관용적으로 처리"하는 래퍼 타입 인식이 없다. `resolveArgument()`가 파라미터 타입이 `Optional`/`List`/`ObjectProvider` 같은 제네릭 래퍼인지 검사하고 내부 타입 인자를 꺼내는 로직이 추가로 필요하다 — 다음 세션으로 미뤘다.
- **우선순위(`@Order`) 기반 후보 선택** — 다루지 않았다.

## 11. Spring 설계 의도

- **왜 "생성자 여럿 + `@Autowired` 없음"일 때 아무것도 추측하지 않는가**: 여러 생성자 중 하나를 휴리스틱(예: 파라미터가 가장 많은 것)으로 고르면, 사용자가 의도하지 않은 생성자가 몰래 선택되어 예측 불가능한 동작이 생길 수 있다. Spring은 모호한 상황에서 "추측해서 맞히기"보다 "명시적이지 않으면 안전한 기본값(기본 생성자)으로 물러난다"는 원칙을 택했다 — 애노테이션이 없다는 것 자체를 "생성자 주입을 원하지 않는다"는 신호로 해석하는 셈이다.
- **왜 파라미터 이름 일치가 `@Qualifier`보다 먼저 검사되는가**: `determineAutowireCandidate`의 주석에 명시돼 있듯, 이는 "명시적 선언"(dependency의 실제 이름)과 "명시적 힌트"(`@Qualifier` 제안 이름)를 같은 종류의 신호로 취급하고 그중 더 근본적인 것(실제 선언된 이름)을 먼저 본다는 설계다. 실무에 미치는 영향은 거의 없다 — 파라미터 이름이 우연히 다른 빈 이름과 겹치는 경우가 드물기 때문이다.
- **왜 `Optional`/`List`/`ObjectProvider`는 서로 다른 방식으로 "관용"을 표현하는가**: `Optional`은 "있을 수도 없을 수도 있는 값"이라는 자바 표준 관용구를 그대로 재사용한다. `List`는 "0개 이상의 컬렉션"이라는 자연스러운 의미를 갖는다. `ObjectProvider`는 둘과 달리 **해석 시점 자체를 늦춘다** — 생성자 실행 시점에는 아직 존재하지 않는 빈을 나중에(다른 빈이 다 만들어진 뒤) 안전하게 조회할 수 있게 해준다. 세 타입 모두 "필수 아님"을 표현하지만, 표현하는 타이밍과 개수의 의미가 다르다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 생성자가 여럿이고 `@Autowired`가 없을 때 Spring은 아무것도 추측하지 않는다 — 기본 생성자가 있으면 그것만 쓰고, 등록된 빈이 있어도 완전히 무시한다.
- 예상과 달랐던 것: 후보 선택의 우선순위에서 파라미터/필드의 순수 이름 일치가 `@Qualifier`보다 먼저 검사된다(소스로 확인, 실무 영향은 미미).
- 예상대로였던 것: 후보 0개/2개 이상(disambiguation 실패)이 모두 `UnsatisfiedDependencyException`으로 귀결된다는 것 — 공식 테스트(`autowireWithTwoMatchesForConstructorDependency`, `autowireWithUnsatisfiedConstructorDependency`)로 정확히 확인됐다.
- Mini 구현의 부수 효과: 생성자 기반 순환 참조 감지가 project 15부터는 진짜 의존성 해석 경로에서 자연스럽게 일어난다 — 4주차에서 정적 필드로 흉내 내야 했던 제약이 사라졌다.
- 새로 열린 질문: `Optional`/`List`/`ObjectProvider` 스타일 mini 주입과 이름 기반 tiebreak는 다음 세션으로 남겨 뒀다. 10주차(`@Primary`, `@Qualifier`와 순환 참조)로 이어진다.
