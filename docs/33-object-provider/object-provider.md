# ObjectProvider — "없음"과 "모호함"을 서로 다르게 완화하는 세 개의 조회 메서드

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`32`](../32-factory-bean/factory-bean.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 9주차(생성자 주입과 의존성 탐색)는 `Optional`/컬렉션/`ObjectProvider` 주입을 실험 목록에 넣어 두고도 깊이 다루지는 않았는데, 이번엔 `ObjectProvider<T>`의 `getObject()`/`getIfAvailable()`/`getIfUnique()` 세 메서드가 "후보 없음"과 "후보 모호함"이라는 두 가지 실패를 각각 어떻게 다르게 처리하는지를 직접 확인한다.

## 1. 이번 질문

- `ObjectProvider<T>`를 생성자에 주입받으면, 그 시점에 실제로 빈을 찾아보는가?
- `getObject()`/`getIfAvailable()`/`getIfUnique()`는 "후보가 0개"와 "후보가 여러 개(모호함)"라는 두 가지 실패를 각각 어떻게 다르게 처리하는가?
- `@Primary`가 있으면 후보가 여러 개여도 모호하지 않은가?
- `stream()`과 `orderedStream()`은 실제로 다른 순서를 돌려주는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Fine-tuning Annotation-based Autowiring with Qualifiers", `ObjectProvider` Javadoc)는 `getIfAvailable()`이 "없으면 `null`"을, `getIfUnique()`가 "유일하지 않으면 `null`"을 돌려준다고 설명한다.
- `ObjectProvider` Javadoc은 드물게 아주 구체적이다 - "`getObject()`는 없으면 `NoSuchBeanDefinitionException`을 던지고, `getIfAvailable()`은 없으면 `null`을 돌려주지만, **둘 다** 모호한 경우(유일한 승자가 없는 다중 후보)에는 `NoUniqueBeanDefinitionException`을 던진다. `getIfUnique()`만은 없는 경우와 모호한 경우를 **둘 다** `null`로 돌려준다"고 명시한다. 이번 실험은 이 세 문장을 그대로 실행으로 재현한 것이다.
- "유일성 판정은 항상 `primary` 플래그를 존중한다"는 것도 Javadoc에 명시돼 있다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `getIfAvailable()`이라는 이름을 보고, "가능하면 준다"는 의미이니 후보가 여러 개라도 예외 없이 `null`을 돌려줄 거라 예상했다 — **틀렸다.** `getIfAvailable()`은 "없음"만 완화해 줄 뿐, "모호함"은 `getObject()`와 똑같이 `NoUniqueBeanDefinitionException`을 그대로 던진다.
- `getIfUnique()`와 `getIfAvailable()`이 사실상 비슷한 관대함을 가질 거라 예상했다 — **틀렸다.** `getIfUnique()`만 "없음"과 "모호함"을 **둘 다** `null`로 완화한다 - 이름이 정확히 그 의미(유일하지 않으면 아무것도 안 준다)를 담고 있었다.
- `ObjectProvider<Greeter>`를 생성자에 주입받으려면 `Greeter` 구현체가 최소 하나는 있어야 할 거라 예상했다 — **틀렸다.** 후보가 0개여도 `GreeterConsumer` 빈 생성 자체는 아무 문제 없이 성공한다 - 실패는 실제로 조회 메서드를 호출하는 시점에야 일어난다.
- `stream()`도 `orderedStream()`처럼 당연히 `@Order`를 존중할 거라 예상했다 — **틀렸다.** `stream()`은 등록 순서를 그대로 따르고, `@Order`를 실제로 적용하는 건 `orderedStream()`뿐이다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/object-provider-lab`](../../experiments/object-provider-lab)

```java
public class GreeterConsumer {
    private final ObjectProvider<Greeter> greeters;
    public GreeterConsumer(ObjectProvider<Greeter> greeters) {
        this.greeters = greeters;   // 이 시점엔 아무것도 조회하지 않는다
    }
}
```

```java
// 후보 0개
greeters.getObject();       // NoSuchBeanDefinitionException
greeters.getIfAvailable();  // null
greeters.getIfUnique();     // null

// 후보 2개, @Primary 없음(모호함)
greeters.getObject();       // NoUniqueBeanDefinitionException
greeters.getIfAvailable();  // NoUniqueBeanDefinitionException  ← "없음"이 아니라서 완화 안 됨
greeters.getIfUnique();     // null                             ← 이것만 완화됨

// 후보 2개, 하나가 @Primary
greeters.getObject();       // @Primary 빈 - 예외 없음
greeters.getIfAvailable();  // @Primary 빈
greeters.getIfUnique();     // @Primary 빈
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ObjectProvider<T>` | `ObjectFactory<T>`를 확장한 인터페이스 - 지연 조회 + 후보 개수에 따라 다르게 완화된 접근자들 |
| `ObjectFactory<T>` | `ObjectProvider`의 상위 인터페이스 - `getObject()` 하나만 선언(예외를 완화하지 않는 원시 계약) |
| `NoSuchBeanDefinitionException` | 후보가 0개일 때 |
| `NoUniqueBeanDefinitionException` | `NoSuchBeanDefinitionException`의 하위 클래스 - 후보가 여러 개인데 유일한 승자가 없을 때 |
| `OrderComparator`/`AnnotationAwareOrderComparator` | `orderedStream()`이 `stream()` 결과에 적용하는 정렬 기준 - `Ordered` 인터페이스와 `@Order` 애노테이션을 함께 고려 |
| (실험) `GreeterConsumer` | 생성자로 `ObjectProvider<Greeter>`만 받아 두고, 실제 조회는 테스트가 호출하는 시점에 일어나게 함 |

## 6. 호출 흐름

```text
GreeterConsumer 생성 (빈 생성 시점)
  → 생성자 파라미터 ObjectProvider<Greeter> 주입
      → 실제 후보 탐색은 일어나지 않음 - DependencyObjectProvider 같은 지연 핸들만 만들어짐
      (그래서 후보가 0개여도 이 시점엔 아무 예외가 안 남)

greeters.getObject() 호출 시점
  → resolveDependency(descriptor, beanName, ...)
      → 후보 0개: NoSuchBeanDefinitionException
      → 후보 1개: 그 빈 반환
      → 후보 여러 개, @Primary 없음: NoUniqueBeanDefinitionException
      → 후보 여러 개, @Primary 하나: 그 @Primary 빈 반환 (모호하지 않음)

greeters.getIfAvailable()
  → try { return getObject(); }
    catch (NoUniqueBeanDefinitionException ex) { throw ex; }   ← 먼저 잡아서 그대로 다시 던짐
    catch (NoSuchBeanDefinitionException ex) { return null; }  ← 이 catch는 더 넓은 상위 타입

greeters.getIfUnique()
  → try { return getObject(); }
    catch (NoSuchBeanDefinitionException ex) { return null; }  ← re-throw 가드가 없어서
                                                                   NoUniqueBeanDefinitionException도
                                                                   여기서 함께 잡힘(하위 타입이므로)

greeters.stream() / orderedStream()
  → stream()        : 후보들을 등록 순서 그대로 나열
  → orderedStream()  = stream().sorted(OrderComparator.INSTANCE류)   ← @Order/Ordered로 재정렬
```

`getIfAvailable()`과 `getIfUnique()`의 catch 블록 구조 차이, 그리고 `stream()`/`orderedStream()`이 갈리는 지점을 함께 그린 다이어그램: [`diagrams/object-provider-resolution.md`](diagrams/object-provider-resolution.md)

## 7. 브레이크포인트

25~32번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 예외가 나는가/안 나는가"라는 결과였고, 그건 실행 결과로 확인하는 쪽이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.ObjectProvider (인터페이스 Javadoc 및 default 메서드 전부)
org.springframework.beans.factory.ObjectProvider#getIfAvailable (catch 순서)
org.springframework.beans.factory.ObjectProvider#getIfUnique (catch 부재)
org.springframework.beans.factory.NoUniqueBeanDefinitionException (상속 관계)
org.springframework.beans.factory.ObjectProvider#orderedStream
```

## 8. 런타임 관찰

[`ObjectProviderTest`](../../experiments/object-provider-lab/src/test/java/lab/experiments/objectprovider/ObjectProviderTest.java) (9개):

| 실험 | 결과 |
| --- | --- |
| 후보 0개, `getObject()` | `NoSuchBeanDefinitionException` |
| 후보 0개, `getIfAvailable()` | `null` |
| 후보 0개, `getIfUnique()` | `null` |
| 후보 1개 | 세 메서드 모두 그 빈 반환 |
| 후보 2개(모호함), `getObject()` | `NoUniqueBeanDefinitionException` |
| 후보 2개(모호함), `getIfAvailable()` | **`NoUniqueBeanDefinitionException`**(완화 안 됨) |
| 후보 2개(모호함), `getIfUnique()` | `null`(완화됨) |
| 후보 2개, 하나가 `@Primary` | 세 메서드 모두 예외 없이 그 `@Primary` 빈으로 수렴 |
| `stream()` vs `orderedStream()`(등록 순서: 프랑스어→영어→한국어, `@Order` 값: 영어(10)→한국어(20)→프랑스어(30)) | `stream()`은 등록 순서 그대로(`Bonjour, Hello, 안녕하세요`), `orderedStream()`은 `@Order` 값 순서(`Hello, 안녕하세요, Bonjour`) |

**직접 겪은 것**: `getIfAvailable()`이 모호한 경우에도 예외를 던진다는 것은 이름만 보고는 전혀 예상하지 못했던 부분이라, 테스트를 작성하기 전에 Javadoc 원문(2번 절)을 두 번 다시 읽어야 했다 - "IfAvailable"이라는 이름이 "존재 여부"에 대한 관대함만 약속하지, "유일성"에 대한 관대함까지 약속하는 게 아니라는 걸 이름만으로는 구분하기 어려웠다. 실제로 소스(`getIfAvailable()`의 catch 블록 순서)를 보고 나서야 이게 우연이 아니라 **의도적으로 두 예외를 구분해서 처리하는 코드**라는 걸 확신할 수 있었다 - `catch (NoUniqueBeanDefinitionException ex) { throw ex; }`가 더 넓은 `catch (NoSuchBeanDefinitionException ex) { return null; }`보다 먼저 오는 순서 자체가 "이건 그냥 흔한 다형성 catch 블록이 아니라, 두 실패를 의도적으로 구분하려는 코드"라는 신호였다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다. 이번 주제는 인터페이스 자신의 Javadoc과 default 메서드 구현 자체가 가장 직접적이고 정확한 명세였다 - 공식 유닛 테스트를 찾아보기 전에 이미 소스가 모든 것을 정확히 말해 주고 있었다.

- `ObjectProvider` 인터페이스 상단 Javadoc의 실제 원문(4번째 문단)을 그대로 확인했다 - "both methods[getObject/getIfAvailable] will throw a NoUniqueBeanDefinitionException", "getIfUnique() will return null both when no matching bean is found and when more than one matching bean is found without a unique winner"라는 문장이 이번 실험 결과와 정확히 일치한다.
- `getIfAvailable()`의 실제 소스: `catch (NoUniqueBeanDefinitionException ex) { throw ex; }`가 `catch (NoSuchBeanDefinitionException ex) { return null; }`보다 먼저 온다는 것을 확인했다 - 자바의 catch 블록은 코드 순서대로 첫 매치를 채택하므로, 이 순서 자체가 "모호함은 다시 던지고, 그 외의 못 찾음만 완화한다"는 로직을 정확히 구현한다.
- `getIfUnique()`의 실제 소스: `NoUniqueBeanDefinitionException`을 따로 잡는 코드가 아예 없다는 것을 확인했다 - `NoUniqueBeanDefinitionException`이 `NoSuchBeanDefinitionException`의 하위 클래스이므로, 유일한 `catch (NoSuchBeanDefinitionException ex) { return null; }`가 두 경우를 모두 잡는다.
- `orderedStream()`의 실제 소스: `stream().sorted(OrderComparator.INSTANCE)`로 정의돼 있고, Javadoc은 "표준 Spring 애플리케이션 컨텍스트에서는 `Ordered`뿐 아니라 `@Order` 애노테이션도 고려한다"고 설명한다 - 실제 구현체(`DefaultListableBeanFactory` 내부)가 `AnnotationAwareOrderComparator`로 이 기본 동작을 오버라이드한다는 뜻이고, 8번 절의 마지막 행이 그걸 실행으로 확인한 것이다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `ObjectProvider`의 지연 조회 자체(빈을 즉시 찾지 않고 나중에 찾는 핸들을 넘긴다)는 9주차에서 이미 `Optional`/컬렉션 주입을 다루며 스쳐 지나간 개념과 크게 다르지 않다 - 이번 주의 진짜 가치는 그 지연 조회가 세 개의 서로 다른 접근자(`getObject`/`getIfAvailable`/`getIfUnique`)로 나뉘어 있고, 그 셋이 "없음"과 "모호함"이라는 두 가지 실패를 각각 다른 조합으로 완화한다는 **세밀한 차이**에 있었다 - mini 구현보다는 세 메서드를 나란히 실행해 표로 정리하는 쪽이 훨씬 명확했다.

## 11. Spring 설계 의도

- **왜 "없음"과 "모호함"을 서로 다른 실패로 구분하는가**: "후보가 없다"는 것은 대체로 "이 기능은 선택적이고, 없으면 안 쓰면 된다"는 의미로 해석할 수 있는 정상적인 상황이다 - 그래서 `getIfAvailable()`은 이걸 조용한 `null`로 완화해 준다. 반면 "후보가 여러 개인데 어느 걸 써야 할지 모른다"는 것은 설정 실수일 가능성이 훨씬 높다 - 어떤 빈이 선택될지 코드 스스로도 확신할 수 없는 상태를 조용히 `null`로 넘겨 버리면, 그 자리에서 당장 터지지 않고 훨씬 나중에(엉뚱한 동작으로) 문제가 드러날 위험이 있다. `getIfAvailable()`이 이 경우만은 예외로 남겨 두는 것은 "완화해도 안전한 애매함"과 "완화하면 위험한 애매함"을 구분한 결과로 보인다.
- **왜 `getIfUnique()`는 그 위험한 애매함까지도 완화하는가**: 이름 자체가 "유일할 때만 달라"는 뜻이다 - "유일하지 않다"는 것 자체가 이미 실패 조건으로 명시돼 있으므로, 그 실패의 세부 원인(0개인지 여러 개인지)까지 호출자에게 다르게 알려줄 필요가 없다. `getIfAvailable()`이 "존재"라는 하나의 축만 다루는 것과 달리, `getIfUnique()`는 "유일성"이라는 또 다른 축을 다루고, 그 축에서는 0개와 여러 개가 사실 **같은 실패**(유일하지 않음)의 두 가지 형태일 뿐이다.
- **왜 `stream()`은 순서를 보장하지 않고 `orderedStream()`을 따로 두었는가**: 정렬은 공짜가 아니다 - 후보가 많을 때 매번 정렬 비용을 감수하고 싶지 않은 호출자도 있을 수 있고, 애초에 순서가 중요하지 않은 용도(단순히 "전부 순회한다")도 많다. 순서가 필요한 소수의 경우를 위해 `orderedStream()`이라는 별도의, 이름으로 그 의도를 분명히 드러내는 메서드를 두고, 기본 `stream()`은 가장 저렴한 경로(등록 순서 그대로)를 유지한 것으로 보인다 - "필요한 비용은 그걸 요청한 코드만 지불한다"는, 이 저장소가 반복해서 봐 온 원칙의 또 다른 사례다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `getIfAvailable()`이라는 이름이 주는 "관대함"의 인상이, 실제로는 "존재 여부"에 대해서만 관대하고 "유일성"에 대해서는 전혀 관대하지 않다는 것 - 메서드 이름만으로 정확한 계약을 추측하는 것이 얼마나 위험한지를 보여준 사례였다. 이번 실험이 아니었다면 "IfAvailable이니까 어떤 상황에서도 예외 없이 null이나 값을 돌려주겠지"라고 막연히 믿었을 것이다.
- 예상 밖이었던 것: 세 메서드의 차이가 우연한 구현 디테일이 아니라, catch 블록 순서(`getIfAvailable`)와 상속 관계를 이용한 catch 생략(`getIfUnique`)이라는 **의도적으로 설계된 예외 처리 패턴**이었다는 것 - 소스를 직접 읽지 않았다면 "그냥 둘 다 비슷하게 관대하겠지"라고 넘어갔을 부분이다.
- 예상대로였던 것(재확인): `@Primary`가 모호함을 해소한다는 것 - 10주차(`@Primary`, `@Qualifier`와 순환 참조)에서 다룬 원칙이 `ObjectProvider`의 세 접근자 전부에 일관되게 적용된다는 것을 재확인했다.
- 새로 배운 것: `stream()`과 `orderedStream()`의 차이 - "정렬 비용은 그걸 요청한 곳만 지불한다"는 설계가, 이 시리즈에서 반복해서 봐 온 "필요한 것만 좁게 확장한다"는 원칙의 성능 관점 버전이라는 것을 확인했다.
