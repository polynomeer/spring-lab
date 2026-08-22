# 제네릭 타입 기반 의존성 해석 — 타입을 몰라서가 아니라 "광고하지 않아서" 못 찾는다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`45`](../45-bean-definition-overriding/bean-definition-overriding.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 9·10주차(의존성 탐색, `@Primary`/`@Qualifier`)는 같은 타입의 빈이 여러 개일 때의 해법을 다뤘지만, "같은 원시 타입인데 제네릭 타입 인자만 다른" 빈들(`Converter<String,Integer>` vs `Converter<Integer,String>`)을 컨테이너가 어떻게 구분하는지는 다룬 적이 없다 - 그리고 그 구분이 `@Bean` 메서드의 반환 타입 선언 방식 하나에 완전히 좌우된다는 것을 이번에 확인한다.

## 1. 이번 질문

- `Converter<String,Integer>`와 `Converter<Integer,String>`처럼 원시 타입은 같고 제네릭 타입 인자만 다른 두 빈을, `@Autowired`가 정확히 구분해서 주입할 수 있는가?
- 그 구분은 실제 빈 객체의 클래스 선언(`implements Converter<String,Integer>`)을 보는 것인가, 아니면 다른 무언가를 보는 것인가?
- `@Bean` 팩토리 메서드의 반환 타입이 원시 타입(`Converter`)이면 어떻게 되는가 - 그래도 실제 객체를 보고 알아서 판단해 주는가?
- 제네릭으로 구분이 안 될 때, 익숙한 해법(`@Qualifier`)이 여전히 통하는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Autowiring Based on Generic Type")는 Spring 4부터 `@Autowired`가 제네릭 타입까지 고려해서 후보를 좁힌다고 설명하고, `Store<String>`과 `Store<Integer>` 같은 예제로 이 기능을 소개한다.
- 문서는 이 기능이 `@Bean` 메서드의 반환 타입에서 제네릭 정보를 읽어 온다고 짧게 언급하지만, 그 반환 타입이 원시 타입으로 선언되면 어떻게 되는지 - 즉 "실제 객체는 제네릭 정보를 갖고 있는데 메서드 시그니처는 그걸 감추고 있는" 상황 - 은 다루지 않는다.
- `NoUniqueBeanDefinitionException`/`UnsatisfiedDependencyException`의 관계나, 이 실패가 정확히 "타입을 못 찾음"인지 "여러 후보 중 못 고름"인지는 API 문서 수준에서 명확히 구분되지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Bean` 메서드가 원시 타입 `Converter`를 반환하도록 선언해도, 컨테이너가 실제 반환된 객체(`StringToIntConverter`)의 클래스 선언을 보고 제네릭 정보를 알아낼 수 있을 거라 예상했다 — **틀렸다.** 컨테이너는 실제 객체를 인스턴스화하지 않고도 타입을 판정하려 하므로(4주차의 지연 생성 원칙과 같은 이유), `@Bean` 메서드의 **선언된 반환 타입**만 보고 판단한다 - 실제 객체가 무엇을 구현하고 있는지는 이 판정에 반영되지 않는다.
- 원시 타입으로 선언되면 컨테이너가 "이 빈은 제네릭 정보가 없다"고 판단해서 아예 후보에서 제외할 거라 예상했다(`Converter<String,Integer>`라는 요구와 안 맞으니) — **틀렸다.** 오히려 정반대로, 원시 타입 선언은 **무엇에든 매칭될 수 있는 후보**로 취급된다 - 그래서 두 원시 타입 빈이 전부 후보에 올라 모호함(`NoUniqueBeanDefinitionException`) 오류가 난다. "안 맞아서 제외"가 아니라 "너무 넓어서 둘 다 맞음"이라는, 정반대 이유로 실패한다.
- 이 모호함을 `@Qualifier`로 풀 수 없을 거라 예상했다(제네릭이라는 특수한 문제니 특수한 해법이 필요할 것 같아서) — **틀렸다.** 10주차에서 이미 배운 평범한 `@Qualifier`가 아무 문제 없이 통한다 - 제네릭 기반 매칭이 실패했을 때 폴백하는 경로가 결국 이름 기반 후보 선택이라는, 이미 알던 메커니즘과 같은 것이었다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/generic-dependency-lab`](../../experiments/generic-dependency-lab)

```java
@Configuration
public class PreservedGenericsConfig {
    @Bean
    public Converter<String, Integer> stringToInt() { return new StringToIntConverter(); }   // 제네릭 보존

    @Bean
    public Converter<Integer, String> intToString() { return new IntToStringConverter(); }

    @Bean
    public StringToIntConsumer consumer(Converter<String, Integer> converter) {
        return new StringToIntConsumer(converter);   // 정확히 stringToInt만 주입됨
    }
}
```

```java
@Configuration
public class ErasedGenericsConfig {
    @Bean @SuppressWarnings("rawtypes")
    public Converter stringToIntRaw() { return new StringToIntConverter(); }   // 제네릭 소거

    @Bean @SuppressWarnings("rawtypes")
    public Converter intToStringRaw() { return new IntToStringConverter(); }

    @Bean
    public StringToIntConsumer consumer(Converter<String, Integer> converter) {
        return new StringToIntConsumer(converter);
    }
    // → NoUniqueBeanDefinitionException: "found 2: stringToIntRaw,intToStringRaw"
}
```

```java
@Bean
public StringToIntConsumer consumer(@Qualifier("stringToIntRaw") Converter<String, Integer> converter) {
    return new StringToIntConsumer(converter);   // 정상 동작
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ResolvableType` | 제네릭 타입 정보를 다루는 Spring의 핵심 추상화 - `@Bean` 메서드 반환 타입, 필드/파라미터 타입에서 이걸 뽑아내 비교 |
| `DefaultListableBeanFactory#doResolveDependency` | 후보 빈들을 찾아서 `DependencyDescriptor`(요구되는 제네릭 타입 포함)와 대조하는 지점 |
| `RootBeanDefinition`의 팩토리 메서드 반환 타입 정보 | 빈을 실제로 인스턴스화하지 않고도 타입을 판정하기 위해, `@Bean` 메서드의 **선언된** 제네릭 반환 타입을 저장해 둠 |
| `NoUniqueBeanDefinitionException` | 후보가 여러 개인데 유일한 승자가 없을 때(33번 `ObjectProvider` 문서에서도 다룸) |
| `@Qualifier` | 제네릭 기반 매칭이 실패(모호함)할 때도 그대로 통하는, 이름 기반 폴백 해법(10주차) |

## 6. 호출 흐름

```text
[PreservedGenericsConfig - @Bean 반환 타입이 Converter<String, Integer>로 선언됨]
consumer(Converter<String, Integer> converter) 파라미터 해석
  → DependencyDescriptor가 요구 타입을 ResolvableType으로 캡처: Converter<String, Integer>
  → 후보 빈 순회: stringToInt(선언된 타입 Converter<String,Integer>), intToString(Converter<Integer,String>)
  → 각 후보의 "선언된" 제네릭 타입과 요구 타입을 ResolvableType끼리 비교
      → stringToInt: 일치 → 후보로 채택
      → intToString: 타입 인자가 다름 → 제외
  → 유일한 후보(stringToInt) 확정 → 정상 주입

[ErasedGenericsConfig - @Bean 반환 타입이 원시 Converter로 선언됨]
consumer(Converter<String, Integer> converter) 파라미터 해석
  → 후보 빈 순회: stringToIntRaw(선언된 타입 Converter, 제네릭 정보 없음), intToStringRaw(마찬가지)
  → 원시 타입 선언은 제네릭 인자와 무관하게 "타입 소거된 채로" 매칭 가능 → 둘 다 후보로 채택
      (실제 객체가 무엇을 implements 하는지는 이 단계에서 보지 않음 - 인스턴스화 전 판정이므로)
  → 후보 2개, 유일한 승자를 정할 근거 없음 → NoUniqueBeanDefinitionException
      → UnsatisfiedDependencyException으로 감싸져서 컨텍스트 시작 실패

[ErasedGenericsWithQualifierConfig - @Qualifier("stringToIntRaw") 추가]
  → 후보 2개인 것은 동일하지만, @Qualifier가 빈 이름을 직접 지정
  → 이름 일치 여부로 즉시 좁혀짐 → 정상 주입
```

`@Bean` 메서드가 "광고하는" 타입과 실제 객체가 "아는" 타입이 갈리는 지점, 그리고 원시 타입 선언이 왜 "제외"가 아니라 "모호함"으로 이어지는지를 함께 그린 다이어그램: [`diagrams/generic-dependency-flow.md`](diagrams/generic-dependency-flow.md)

## 7. 브레이크포인트

25~45번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 예외가 나는가, 후보가 몇 개로 잡히는가"라는 결과였고, 예외 메시지 자체(`"found 2: stringToIntRaw,intToStringRaw"`)가 이미 그 답을 정확히 알려줬다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 확인하려 했으나, 이번 주제는 예외 메시지 자체가 이미 충분히 구체적이라 별도의 심층 소스 추적 없이도 8·9번 절의 관찰을 확정할 수 있었다 - 정직하게 밝혀 둔다.

```text
org.springframework.beans.factory.support.DefaultListableBeanFactory#doResolveDependency
org.springframework.beans.factory.config.DependencyDescriptor#resolveNotUnique
```

## 8. 런타임 관찰

[`GenericDependencyTest`](../../experiments/generic-dependency-lab/src/test/java/lab/experiments/genericdep/GenericDependencyTest.java) (3개):

| 실험 | 결과 |
| --- | --- |
| `@Bean` 반환 타입이 제네릭 보존(`Converter<String,Integer>`) | `Converter<String,Integer>`만 정확히 주입됨(`convert("42") == 42`) |
| `@Bean` 반환 타입이 원시 타입(`Converter`)으로 소거 | `UnsatisfiedDependencyException` ← `NoUniqueBeanDefinitionException`("found 2: stringToIntRaw,intToStringRaw") |
| 같은 원시 타입 소거 상황에 `@Qualifier("stringToIntRaw")` 추가 | 정상 주입 |

**직접 겪은 것**: 두 번째 실험을 처음 작성했을 때, `AnnotationConfigApplicationContext(ErasedGenericsConfig.class)`처럼 설정 클래스를 생성자에 바로 넘기고 `assertThatThrownBy(context::refresh)`로 예외를 잡으려 했는데, 테스트가 `AssertionError`가 아니라 **원본 예외 자체**를 던지며 실패했다 - 그 단일 인자 생성자가 `register()`와 `refresh()`를 이미 내부적으로 실행해 버려서, 예외가 `assertThatThrownBy`의 람다에 도달하기도 전에 try-with-resources 시작 지점에서 이미 터져 있었던 것이다. 무인자 생성자로 컨텍스트를 만들고 `register()`를 따로 호출한 뒤에야 `refresh()` 호출 자체를 람다로 감쌀 수 있었다 - 이 저장소 여러 곳에서 이미 써 온 패턴인데도, 이번엔 그 이유(정확히 언제 예외가 나는지 통제해야 하는 테스트라는 것)를 다시 한번 새기게 됐다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, 8번 절의 실행 결과(특히 예외 메시지 자체)가 이미 충분히 구체적인 명세 역할을 했다 - `"expected single matching bean but found 2: stringToIntRaw,intToStringRaw"`라는 메시지가, "타입이 안 맞아서 후보가 0개"가 아니라 "후보가 정확히 2개라서 못 고른다"는 실패 성격을 그 자체로 증명한다. 이건 17번(`SpringApplication`) 문서가 "실행 결과와 바이트코드만으로도 충분히 확인할 수 있다"는 걸 보여줬던 것과 같은 정신이다 - 이번엔 예외 메시지 하나가 그 역할을 했다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. 제네릭 타입 비교 자체(`ResolvableType`)는 자바 리플렉션의 `ParameterizedType` API 위에 상당히 복잡한 계층을 쌓은 것이라, 축소 구현이 이번 주의 핵심 질문(제네릭 정보가 어디서 오는가, 왜 소거되면 "제외"가 아니라 "모호함"이 되는가)에 비해 지나치게 큰 부담이었다 - 9·10주차의 `mini-container`가 이미 다룬 타입 기반 후보 탐색 골격 위에, 이번 주에 확인한 규칙("선언된 반환 타입만 본다")을 개념적으로 얹어 이해하는 것으로 충분했다.

## 11. Spring 설계 의도

- **왜 컨테이너는 실제 객체가 아니라 `@Bean` 메서드의 "선언된" 반환 타입만 보는가**: 4주차부터 반복해서 확인해 온 원칙 - 빈 타입을 판정하기 위해 그 빈을 실제로 인스턴스화하는 것은 피해야 한다(순환 참조, 초기화 순서, 부작용 있는 생성자 등의 문제를 만들 수 있으므로). `@Bean` 메서드의 시그니처는 인스턴스화 없이도 읽을 수 있는 정적 정보다 - 컨테이너는 이 저장소가 32번(`FactoryBean`)의 `getObjectType()`에서 이미 본 것과 똑같은 이유로, "미리 알 수 있는 것"(메서드 시그니처)만 신뢰하고 "실행해야 알 수 있는 것"(실제 반환값)은 타입 판정에 쓰지 않는다.
- **왜 원시 타입 선언이 "제외"가 아니라 "모호함"으로 이어지는가**: 원시 타입 사용은 자바에서 "이 타입의 제네릭 정보를 신경 쓰지 않겠다"는 명시적인 선택이다(컴파일러가 unchecked 경고를 내는 것도 이 때문이다) - Spring은 이걸 "이 빈은 제네릭 요구사항과 무관하게 호환될 수 있다"는 더 관대한 신호로 해석한다. 만약 원시 타입 선언이 무조건 제외당한다면, 제네릭을 아직 안 쓰는 레거시 코드나 의도적으로 제네릭을 우회한 코드가 자동 주입에서 완전히 배제되는 훨씬 가혹한 결과가 됐을 것이다 - "정보가 없으면 안전하게 배제한다" 대신 "정보가 없으면 후보로는 남겨 두되, 그래서 여러 개가 남으면 모호함으로 실패한다"는 쪽을 택한 것이다.
- **왜 제네릭 기반 매칭이 실패해도 `@Qualifier`라는 같은 해법이 통하는가**: 제네릭 타입 인자는 결국 "여러 후보 중 하나를 정확히 지목하기 위한 또 다른 방법"일 뿐이다 - `@Primary`/`@Qualifier`(10주차)도 정확히 같은 문제(후보가 여럿일 때 하나를 고른다)를 다른 축(우선순위, 이름)으로 푸는 방법이다. Spring의 의존성 해석 파이프라인은 "후보를 좁히는 여러 단계"(타입 → 제네릭 → `@Primary` → `@Qualifier`/이름)를 순서대로 적용하는 구조이므로, 어느 한 단계(제네릭)가 후보를 못 좁혀도 그다음 단계(`@Qualifier`)가 여전히 남은 후보들 위에서 정상적으로 작동한다 - 이 저장소가 반복해서 봐 온 "확장점을 단계별로 쌓는다"는 원칙이 의존성 해석 파이프라인 자체의 구조이기도 했다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 원시 타입으로 반환 타입을 선언했을 때의 실패가 "타입이 안 맞아서"가 아니라 "타입 정보가 없어서 오히려 뭐든 다 맞아 버려서"였다는 것 - 처음엔 이 둘의 차이가 사소해 보였는데, 예외 메시지가 "찾는 타입이 없다"가 아니라 "후보가 2개다"라고 정확히 말해 주는 걸 보고 나서야 그 방향이 완전히 반대라는 걸 실감했다.
- 예상 밖이었던 것: 컨테이너가 실제 객체의 클래스 선언(`implements Converter<String,Integer>`)을 전혀 신경 쓰지 않는다는 것 - "결국 실행하면 다 알 수 있는 정보인데 왜 못 쓰지"라는 직관이, "실행하기 전에 판단해야 한다"는 이 저장소가 4주차부터 반복해서 봐 온 제약 앞에서 다시 한번 꺾였다.
- 예상대로였던 것(재확인): `@Qualifier`가 여전히 통한다는 것 - 10주차에서 배운 해법이 완전히 새로운 문제(제네릭 모호함)에도 그대로 적용된다는 걸 보면서, Spring의 의존성 해석이 "문제마다 새 해법"이 아니라 "몇 가지 표준 해법을 여러 단계에서 재사용한다"는 설계라는 걸 다시 확인했다.
- 새로 배운 것: 이 시리즈에서 반복해서 본 "선언된 것만 신뢰하고 실제 실행 결과는 신뢰하지 않는다"는 원칙(32번의 `getObjectType()`, 30번의 이름 기반 계약)이, 이번엔 "제네릭 타입 인자"라는 완전히 다른 축에서도 똑같이 나타났다 - `@Bean` 메서드 시그니처가 "광고"하는 것과 실제 객체가 "아는" 것이 다를 수 있고, Spring은 일관되게 전자만 믿는다는 걸 이번에 가장 분명하게 확인했다.
