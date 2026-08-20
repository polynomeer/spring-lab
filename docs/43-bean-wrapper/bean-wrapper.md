# BeanWrapper — "자동 확장"이라는 말 하나에 실제로는 서로 다른 두 스위치가 숨어 있다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`42`](../42-task-decorator/task-decorator.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 1주차(`bean-factory-getbean.md`)가 `BeanWrapper`를 스치듯 언급한 뒤로 한 번도 정면으로 다룬 적이 없다 - `"address.city"` 같은 점 표기 중첩 경로와 `"tags[2]"` 같은 인덱스 표기가 실제로 어떻게 해석되는지, 그리고 "자동으로 늘어난다"는 것이 사실은 하나가 아니라 **서로 다른 두 개의 독립적인 스위치**로 나뉘어 있다는 것을 직접 확인한다.

## 1. 이번 질문

- `"address.city"`처럼 중첩된 프로퍼티 경로에서, 중간 객체(`address`)가 `null`이면 어떻게 되는가?
- `"tags[2]"`처럼 인덱스가 있는 프로퍼티에서, 그 인덱스가 현재 컬렉션 크기를 넘어서면 어떻게 되는가?
- "자동으로 늘어나게 한다"는 설정(`autoGrowNestedPaths`)이 이 두 상황(중첩 `null` 객체, 컬렉션 인덱스 초과) 모두에 똑같이 적용되는가?
- `Map` 프로퍼티는 리스트/배열과 같은 규칙을 따르는가?
- Spring MVC의 폼/요청 바인딩(`DataBinder`)은 순수 `BeanWrapperImpl`과 같은 기본값을 쓰는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Setting and Getting Basic and Nested Properties")는 `BeanWrapper`가 점 표기(`"address.city"`)와 인덱스 표기(`"tags[2]"`, `"attributes[color]"`)를 모두 지원한다고 설명하고, `setAutoGrowNestedPaths(true)`를 설정하면 경로 중간의 `null` 객체를 자동으로 채워 준다고 설명한다.
- 문서는 `autoGrowCollectionLimit`이라는 별도 속성도 언급하지만, "컬렉션 자동 확장의 상한을 정한다"고만 설명할 뿐, 이게 `autoGrowNestedPaths`와 같은 스위치인지 다른 스위치인지는 명확히 구분해 주지 않는다 - 이번 실험이 실행으로 그 둘을 분리했다.
- `DataBinder`가 `autoGrowNestedPaths`를 어떤 기본값으로 시작하는지는 별도로 언급되지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `autoGrowNestedPaths`가 "자동 확장"이라는 이름 그대로, 중첩 `null` 객체 채우기와 컬렉션 인덱스 확장 **둘 다**를 하나의 스위치로 통제할 거라 예상했다 — **틀렸다.** 소스를 읽어 보니 `getPropertyValue`(조회) 경로에서는 그렇게 보였지만, 실제로 실행해서 확인한 `setPropertyValue`(설정) 경로는 완전히 달랐다 - 컬렉션 인덱스 확장은 `autoGrowNestedPaths`를 전혀 확인하지 않고, 별도의 `autoGrowCollectionLimit`(기본값 `Integer.MAX_VALUE`, 사실상 "항상 허용")이라는 완전히 다른 스위치로 결정된다.
- 그래서 "빈 리스트에 `tags[2]`를 설정하면 실패할 것"이라 예상했다(기본값이 `false`인 `autoGrowNestedPaths`를 켜지 않았으므로) — **틀렸다.** 기본 설정 그대로도 성공했다 - 이 스위치는 애초에 관여하지 않기 때문이다.
- `Map` 프로퍼티도 리스트처럼 어떤 상한이나 스위치의 영향을 받을 거라 예상했다 — **틀렸다.** `Map`은 "확장"이라는 개념 자체가 없다(어떤 키든 그냥 추가하면 됨) - 두 스위치 중 어느 쪽도 관여하지 않는다.
- Spring MVC의 폼 바인딩(`DataBinder`)이 `BeanWrapperImpl`을 내부적으로 쓴다는 것은 알고 있었지만, 그 기본 설정까지 `BeanWrapperImpl`의 기본값(`autoGrowNestedPaths=false`)을 그대로 물려받을 거라 예상했다 — **틀렸다.** `DataBinder`는 이 기본값을 `true`로 뒤집어 둔다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/bean-wrapper-lab`](../../experiments/bean-wrapper-lab)

```java
BeanWrapper wrapper = new BeanWrapperImpl(new Person());   // address는 null, tags는 빈 리스트

wrapper.setPropertyValue("address.city", "Seoul");
// → NullValueInNestedPathException (autoGrowNestedPaths 기본값 false)

wrapper.setPropertyValue("tags[2]", "third");
// → 성공! tags == [null, null, "third"]  (autoGrowCollectionLimit 기본값이 사실상 무제한이므로)
```

```java
BeanWrapper wrapper = new BeanWrapperImpl(new Person());
wrapper.setAutoGrowCollectionLimit(1);
wrapper.setPropertyValue("tags[2]", "third");
// → InvalidPropertyException (index(2) >= limit(1)이라 이번엔 확장 자체를 안 함)
```

```java
DataBinder binder = new DataBinder(person);
binder.isAutoGrowNestedPaths();   // true (BeanWrapperImpl 기본값과 정반대)
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `BeanWrapper`/`BeanWrapperImpl` | 점 표기·인덱스 표기 프로퍼티 경로를 리플렉션으로 읽고 쓰는 핵심 구현체 - `ConversionService`(30번)와도 연동해서 타입 변환까지 함께 처리 |
| `AbstractPropertyAccessor` | `autoGrowNestedPaths` 필드(기본값 `false`)를 실제로 갖고 있는 상위 클래스 |
| `AbstractNestablePropertyAccessor` | `autoGrowCollectionLimit` 필드(기본값 `Integer.MAX_VALUE`)를 갖고 있고, 점/인덱스 경로 파싱과 실제 읽기/쓰기 로직 전체를 담당 |
| `AbstractNestablePropertyAccessor#processKeyedProperty` | 인덱스가 있는 프로퍼티의 **설정**(set) 로직 - 배열/리스트는 `autoGrowCollectionLimit`만 확인, `Map`은 아무 제한도 확인하지 않음 |
| `DataBinder` | Spring MVC `@ModelAttribute`/`WebDataBinder`가 실제로 쓰는 상위 바인딩 계층 - `autoGrowNestedPaths` 기본값을 `true`로 재정의 |

## 6. 호출 흐름

```text
wrapper.setPropertyValue("address.city", "Seoul")
  → 경로를 "address"(중첩 프로퍼티)와 "city"(최종 프로퍼티)로 분리
  → getPropertyValue("address") 조회 → null
  → if (isAutoGrowNestedPaths()) {                    ← AbstractPropertyAccessor의 필드, 기본 false
        setDefaultValue("address")                      새 Address() 인스턴스를 만들어 채움
    } else {
        throw new NullValueInNestedPathException(...)    ← 기본 경로
    }

wrapper.setPropertyValue("tags[2]", "third")
  → processKeyedProperty(tokens, pv)                   ← 인덱스 있는 프로퍼티의 SET 전용 경로
      → propValue = 현재 tags 리스트(빈 리스트)
      → propValue instanceof List
          → index(2) >= size(0) && index(2) < autoGrowCollectionLimit(기본 Integer.MAX_VALUE)?
              → 참 (거의 항상 참) → null로 채우며 리스트를 늘림 → 마지막에 값 추가
          → (autoGrowCollectionLimit을 낮게 설정해서 이 조건이 거짓이 되면)
              → list.set(index, ...) 시도 → IndexOutOfBoundsException → InvalidPropertyException으로 재포장

wrapper.setPropertyValue("attributes[color]", "blue")
  → processKeyedProperty → propValue instanceof Map
      → 조건 확인 없이 그냥 map.put(key, value)          ← 두 스위치 중 어느 것도 관여 안 함

new DataBinder(person)
  → 생성자에서 필드 초기값 this.autoGrowNestedPaths = true로 시작   ← BeanWrapperImpl과 다른 기본값
```

`autoGrowNestedPaths`(중첩 `null` 객체용)와 `autoGrowCollectionLimit`(컬렉션 확장용)이 서로 다른 코드 경로에서 독립적으로 확인되는 지점을 함께 그린 다이어그램: [`diagrams/bean-wrapper-grow-switches.md`](diagrams/bean-wrapper-grow-switches.md)

## 7. 브레이크포인트

25~42번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 예외가 나는가/안 나는가"라는 결과였고, 실행 결과로 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.AbstractPropertyAccessor#autoGrowNestedPaths (필드 선언부)
org.springframework.beans.AbstractNestablePropertyAccessor#autoGrowCollectionLimit (필드 선언부)
org.springframework.beans.AbstractNestablePropertyAccessor#processKeyedProperty
org.springframework.beans.AbstractNestablePropertyAccessor#getPropertyValue (인덱스 조회 경로 - 설정 경로와 다름)
org.springframework.validation.DataBinder (autoGrowNestedPaths 기본값 재정의 지점)
```

## 8. 런타임 관찰

[`BeanWrapperTest`](../../experiments/bean-wrapper-lab/src/test/java/lab/experiments/beanwrapper/BeanWrapperTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| 중첩 경로 `"address.city"`, 중간 객체 `null`, 기본 설정 | `NullValueInNestedPathException` |
| 같은 상황, `autoGrowNestedPaths(true)` | 성공 - `Address` 인스턴스가 자동으로 만들어짐 |
| 인덱스 경로 `"tags[2]"`, 빈 리스트, 기본 설정(`autoGrowNestedPaths` 여전히 `false`) | **성공** - `[null, null, "third"]` |
| 같은 상황, `autoGrowCollectionLimit(1)`로 낮춤 | `InvalidPropertyException` |
| 맵 경로 `"attributes[color]"`, 아무 설정 없음 | 성공 - 그냥 `put()`됨 |
| `new DataBinder(person)`의 `autoGrowNestedPaths` 기본값 | `true`, 그리고 실제로 `"address.city"` 바인딩도 아무 설정 없이 성공 |

**직접 겪은 것**: 세 번째 실험(빈 리스트에 `tags[2]` 설정)은 이번 문서에서 가장 크게 겪은 반전이었다. `getPropertyValue()`(조회 경로)의 소스만 먼저 읽고 "인덱스 컬렉션 확장도 `autoGrowNestedPaths`가 지배하겠구나"라고 결론 내린 뒤 테스트를 작성했는데, 실행해 보니 예외 없이 그냥 성공해 버렸다 - 처음엔 테스트 코드가 잘못됐다고 의심했다. `setPropertyValue()`(설정 경로)의 `processKeyedProperty()`를 별도로 찾아 읽고 나서야, 조회와 설정이 **서로 다른 메서드, 서로 다른 게이트**를 쓴다는 것을 확인했다 - 같은 클래스 안에서도 "읽기"와 "쓰기"가 반드시 같은 규칙을 공유하는 건 아니라는 걸 이번에 처음으로 명확하게 겪었다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `AbstractPropertyAccessor`의 실제 소스: `private boolean autoGrowNestedPaths = false;`를 확인했다 - `BeanWrapperImpl`이 별도로 오버라이드하지 않는 한 이 기본값을 그대로 물려받는다.
- `AbstractNestablePropertyAccessor#processKeyedProperty`의 실제 소스: 배열/리스트 분기 모두 `index >= size && index < this.autoGrowCollectionLimit`이라는 조건만 확인하고, `isAutoGrowNestedPaths()`는 이 메서드 전체에서 단 한 번도 호출되지 않는다는 것을 확인했다 - 세 번째 실험 결과의 정확한 근거다. 반면 같은 클래스의 `getPropertyValue()`(조회용 인덱스 처리)는 `isAutoGrowNestedPaths()`를 명시적으로 확인한다 - 조회와 설정이 서로 다른 정책을 쓴다는 것을 코드로 재확인했다.
- `Map` 분기(`processKeyedProperty`의 세 번째 `else if`)는 `autoGrowCollectionLimit`이나 `autoGrowNestedPaths` 중 어느 것도 확인하지 않고 곧장 `map.put(...)`로 이어진다는 것을 확인했다 - "컬렉션 확장"이라는 개념이 배열/리스트에는 있지만 `Map`에는 애초에 성립하지 않는다는 것의 근거다.
- `DataBinder`의 실제 소스: `private boolean autoGrowNestedPaths = true;`를 확인했다 - `AbstractPropertyAccessor`(그리고 그걸 상속하는 `BeanWrapperImpl`)의 `false`와 정반대다. `DataBinder`가 내부적으로 `BeanWrapperImpl`을 만들 때 이 값을 그대로 전달한다는 것도 함께 확인했다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. 점 표기/인덱스 표기 경로 파싱 자체는 문자열을 토큰으로 나누는 정도의 복잡도라 재구현할 만한 새로운 학습이 아니다 - 이번 주의 가치는 "자동 확장"이라는 하나의 개념어가 실제로는 서로 다른 두 스위치(`autoGrowNestedPaths`, `autoGrowCollectionLimit`)로 갈라져 있고, 그 둘이 서로 다른 코드 경로(조회 vs 설정, 중첩 객체 vs 컬렉션)에 적용된다는 **세밀한 경계**를 확인하는 데 있었다.

## 11. Spring 설계 의도

- **왜 중첩 `null` 객체 자동 생성과 컬렉션 자동 확장이 서로 다른 스위치인가**: 전자는 "이 프로퍼티 경로 자체가 원래 존재하지 않는 객체 그래프를 새로 만들어 낸다"는, 훨씬 근본적이고 위험도가 높은 동작이다(어떤 생성자를 쓸지, 부작용이 있는 생성자는 아닌지 등을 고려해야 함) - 반면 컬렉션에 인덱스로 값을 채우는 것은 "이미 존재하는 컬렉션 안에 빈 자리를 메운다"는 훨씬 국소적인 동작이다. 폼 필드가 배열/리스트로 매핑되는 것(`items[0].name`, `items[1].name` 같은 다중 입력 필드)은 웹 폼 바인딩에서 대단히 흔한 패턴이라, 컬렉션 확장은 기본적으로 허용해 두는 것이 실용적이다 - 반면 임의의 중첩 객체 그래프를 아무 설정 없이 마구 생성하는 것은 그보다 훨씬 신중해야 할 결정이라 기본값을 꺼 둔 것으로 보인다.
- **왜 `DataBinder`는 `autoGrowNestedPaths`의 기본값을 뒤집는가**: `DataBinder`의 전형적인 용도(HTML 폼이나 요청 파라미터를 객체 그래프로 바인딩하는 것)에서는, `"address.city"`처럼 중첩된 필드 이름이 있다는 것 자체가 "이 객체 그래프를 만들어 달라"는 사용자의 명확한 의도다 - 폼을 채우는 사람은 애초에 "주소가 있는 사람"을 표현하려는 것이지, 중간 객체가 없으면 실패하길 기대하지 않는다. 순수 `BeanWrapperImpl`(더 범용적인, 프로그래밍적으로 직접 쓰는 저수준 도구)은 그런 특정 용도를 가정하지 않으므로 더 보수적인 기본값(꺼짐)을 유지하는 것이 안전하다 - "이 도구가 어떤 맥락에서 주로 쓰이는가"에 따라 같은 옵션의 기본값이 달라질 수 있다는 사례다.
- **왜 `Map`은 두 스위치 모두에서 자유로운가**: 배열/리스트는 "크기"라는 물리적 제약이 있는 자료구조라, 인덱스 5에 값을 넣으려면 0~4번 자리도 뭔가로 채워야 한다는 근본적인 문제가 있다 - 그래서 "그 빈 자리를 자동으로 채워도 되는가"라는 질문이 의미가 있다. `Map`은애초에 그런 순서/크기 제약이 없는 자료구조다 - 키 하나를 추가하는 것이 다른 어떤 것에도 영향을 주지 않으므로, "확장"이라는 개념 자체가 성립하지 않는다. 있지도 않은 문제에 대한 스위치를 만들지 않은 것뿐이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: "자동 확장"이라는 하나의 이름 아래 실제로는 서로 다른 두 스위치가 있었다는 것 - `getPropertyValue()`의 소스만 읽고 세운 가설이 `setPropertyValue()`의 실제 실행 결과와 어긋나는 것을 직접 겪고 나서야, 같은 클래스의 "조회"와 "설정"이 반드시 같은 정책을 공유하지 않을 수 있다는 걸 배웠다. 소스의 일부만 읽고 전체를 짐작하는 것이 얼마나 위험한지를 이번 주가 가장 분명하게 보여줬다.
- 예상 밖이었던 것: 컬렉션 자동 확장이 기본적으로 **켜져 있다**(`autoGrowCollectionLimit`이 사실상 무제한)는 것 - "자동으로 뭔가를 만들어 내는 기능은 대체로 기본값이 꺼져 있을 것"이라는 이 저장소가 은연중에 쌓아 온 직관(예를 들어 25~34번에서 본 여러 "명시적으로 켜야 하는" 기능들)이 이번엔 반대로 틀렸다.
- 예상대로였던 것(재확인): `Map`은 리스트/배열과 근본적으로 다른 취급을 받는다는 것 - "순서와 크기가 있는 자료구조"와 "그런 제약이 없는 자료구조"의 차이가, 이 저장소가 여러 번 강조해 온 "문제의 성격이 다르면 해법도 달라야 한다"는 원칙을 다시 확인시켜 줬다.
- 새로 배운 것: 같은 기능(자동 확장)이라도 그걸 쓰는 계층(순수 `BeanWrapperImpl` vs 그 위에 얹힌 `DataBinder`)에 따라 기본값이 정반대로 뒤집힐 수 있다는 것 - "합리적인 기본값"은 절대적인 것이 아니라 그 도구가 실제로 쓰이는 맥락에 따라 다시 판단해야 한다는 걸, 30번(`ConversionService`)·34번(`@Profile`)에서 이미 여러 번 봐 온 "명시적 요청이 암묵적 기본값을 이긴다"는 원칙과는 또 다른 각도에서 확인했다.
