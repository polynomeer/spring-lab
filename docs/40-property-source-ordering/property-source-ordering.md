# PropertySource 순서 — 우선순위는 값이 아니라 목록 안의 "자리"다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`39`](../39-message-source/message-source.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. `Environment`는 [`30-conversion-service`](../30-conversion-service/conversion-service.md)(`@Value` 타입 변환)와 [`34-profile-condition`](../34-profile-condition/profile-condition.md)(`@Profile` 활성화 판정)에서 이미 두 번 다뤘지만, `Environment`가 애초에 "여러 `PropertySource`를 어떤 순서로 뒤져서 값 하나를 결정하는가"라는 가장 기초적인 메커니즘 자체는 아직 정면으로 본 적이 없었다 - `MutablePropertySources`의 순서 조작(`addFirst`/`addLast`/`addBefore`)과 중첩 플레이스홀더 해석을 이번에 직접 확인한다.

## 1. 이번 질문

- `StandardEnvironment`가 기본으로 갖고 있는 두 `PropertySource`(시스템 프로퍼티, 환경 변수) 중 어느 쪽이 우선하는가?
- 같은 키가 여러 `PropertySource`에 있으면, 그 우선순위는 무엇이 결정하는가 - 추가된 시점인가, 목록 안의 위치인가?
- 실행 시점에 `-D`로 준 시스템 프로퍼티가 파일 기반 설정을 덮어쓸 수 있는 이유는 정확히 무엇인가?
- `${a:${b}}`처럼 플레이스홀더의 기본값 자리에 또 다른 플레이스홀더가 있으면 어떻게 해석되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("The Environment Abstraction", "PropertySource")는 `Environment`가 여러 `PropertySource`를 순서가 있는 집합(`MutablePropertySources`)으로 관리하고, "먼저 등록된 `PropertySource`가 우선한다"고 설명한다.
- 문서는 `StandardEnvironment`가 기본으로 `systemProperties`와 `systemEnvironment` 두 `PropertySource`를 갖고 있고, "시스템 프로퍼티가 환경 변수보다 우선한다"고 명시한다 - 하지만 그게 코드 몇 줄의 어떤 메서드 호출 순서로 구현되는지는 다루지 않는다.
- 플레이스홀더 문법(`${key:defaultValue}`)은 설명하지만, 그 `defaultValue` 자리에 또 다른 `${...}`가 올 수 있다는 것과 그게 재귀적으로 해석된다는 것은 예제 없이는 확신하기 어렵다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `systemProperties`와 `systemEnvironment` 중 어느 쪽이 우선인지는 관례적으로 정해져 있을 뿐, 코드로 명확히 강제되지는 않을 거라 예상했다 — **틀렸다.** `StandardEnvironment#customizePropertySources()`가 `systemProperties`를 먼저 `addLast()`하고 `systemEnvironment`를 그다음에 `addLast()`한다 - 코드 두 줄의 순서 그 자체가 우선순위를 결정한다.
- `PropertySource`의 우선순위는 "언제 추가했는가"(먼저 추가한 것이 이긴다, 예를 들어 뒤에 오는 게 못 덮어쓴다는 식)로 정해질 거라 예상했다 — **틀렸다.** 우선순위는 순수하게 목록 안에서의 **위치**다 - 같은 두 `PropertySource`를 순서만 바꿔서 다시 추가하면 승자가 그대로 뒤바뀐다. "언제"가 아니라 "어디"가 전부다.
- 중첩된 플레이스홀더(`${outer:${inner}}`)는 지원되지 않거나, 최소한 바깥쪽 플레이스홀더 이름 자체에 `$`가 포함된 이상한 문자열로 취급될 거라 예상했다 — **틀렸다.** 기본값 부분에 있는 안쪽 플레이스홀더를 먼저 해석한 뒤, 그 결과를 기본값으로 사용한다 - 완전히 자연스러운 재귀 해석이었다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/property-source-ordering-lab`](../../experiments/property-source-ordering-lab)

```java
StandardEnvironment environment = new StandardEnvironment();
// 기본 순서: [systemProperties, systemEnvironment]

MutablePropertySources sources = environment.getPropertySources();
sources.addLast(new MapPropertySource("low", Map.of("greeting", "from-low")));
sources.addFirst(new MapPropertySource("high", Map.of("greeting", "from-high")));

environment.getProperty("greeting");   // "from-high" - 삽입 순서(low가 먼저)와 무관
```

```java
sources.addBefore("anchor", new MapPropertySource("before-anchor", Map.of("greeting", "x")));
// "anchor" 바로 앞자리에 삽입 - addFirst처럼 맨 앞이 아니라 상대 위치
```

```java
System.setProperty("app.name", "from-system-property");
environment.getPropertySources()
        .addLast(new MapPropertySource("applicationConfig", Map.of("app.name", "from-application-config")));

environment.getProperty("app.name");   // "from-system-property" - 기본 systemProperties가 항상 더 앞자리
```

```java
environment.resolvePlaceholders("${outer:${inner}}");   // inner=resolved-inner일 때 "resolved-inner"
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `MutablePropertySources` | `PropertySource`들의 순서가 있는 컬렉션 - `addFirst`/`addLast`/`addBefore`/`addAfter`/`remove` |
| `PropertySource<T>` | 키-값 조회의 최소 단위 - `getProperty(String)` 하나 |
| `PropertySourcesPropertyResolver` | `MutablePropertySources`를 순서대로 순회하며 **첫 매치**를 채택하는 조회 로직 |
| `StandardEnvironment#customizePropertySources` | `systemProperties`(먼저)와 `systemEnvironment`(다음)를 기본으로 등록하는 지점 |
| `PropertyPlaceholderHelper` | `${...}` 문법을 파싱하고, 기본값 부분을 포함해 재귀적으로 치환하는 저수준 파서 |

## 6. 호출 흐름

```text
new StandardEnvironment()
  → AbstractEnvironment 생성자 → customizePropertySources(this.propertySources)
      → StandardEnvironment가 오버라이드:
          propertySources.addLast(new PropertiesPropertySource("systemProperties", ...))
          propertySources.addLast(new SystemEnvironmentPropertySource("systemEnvironment", ...))
      (빈 목록에 addLast를 두 번 하면: [systemProperties, systemEnvironment] 순서로 확정)

environment.getProperty(key)
  → PropertySourcesPropertyResolver#getProperty
      → for (PropertySource<?> source : this.propertySources) {   ← 목록 순서 그대로 순회
            Object value = source.getProperty(key)
            if (value != null) return value   ← 첫 매치에서 즉시 반환, 뒤는 확인 안 함
        }
      → 끝까지 못 찾으면 null (또는 기본값)

environment.resolvePlaceholders("${outer:${inner}}")
  → PropertyPlaceholderHelper#parseStringValue
      → "${outer:...}" 파싱 → key="outer", 콜론 뒤는 defaultValue 부분("${inner}")
      → key "outer"를 먼저 조회 → 없음
      → defaultValue 부분("${inner}") 자체도 플레이스홀더 문법이므로, 그걸 재귀적으로
        다시 parseStringValue() → key "inner" 조회 → "resolved-inner"
      → 최종 결과: "resolved-inner"
```

`addFirst`/`addLast`/`addBefore`가 만드는 목록 순서와, 그 순서가 곧 조회 우선순위가 되는 지점을 함께 그린 다이어그램: [`diagrams/property-source-precedence.md`](diagrams/property-source-precedence.md)

## 7. 브레이크포인트

25~39번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 값이 이기는가"라는 결과였고, `getProperty()`로 직접 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.core.env.StandardEnvironment#customizePropertySources
org.springframework.core.env.PropertySourcesPropertyResolver#getProperty
org.springframework.core.env.MutablePropertySources (addFirst/addLast/addBefore 전체)
org.springframework.util.PropertyPlaceholderHelper#parseStringValue
```

## 8. 런타임 관찰

[`PropertySourceOrderingTest`](../../experiments/property-source-ordering-lab/src/test/java/lab/experiments/propertysource/PropertySourceOrderingTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| `StandardEnvironment` 기본 `PropertySource` 순서 | `[systemProperties, systemEnvironment]` - 이 순서 그대로 |
| `addFirst`로 넣은 것 vs `addLast`로 넣은 것(같은 키) | 삽입 순서와 무관하게 `addFirst` 쪽이 항상 이김 |
| 같은 두 `PropertySource`의 위치만 서로 바꿈 | 승자가 그대로 뒤바뀜 - 값이 아니라 위치가 결정 |
| `addBefore("anchor", ...)` | `anchor` 바로 앞자리에 정확히 삽입됨 |
| 실행 시점 시스템 프로퍼티 vs `addLast`로 추가한 커스텀 설정(같은 키) | 시스템 프로퍼티가 이김(기본 `systemProperties`가 항상 더 앞자리이므로) |
| `${outer:${inner}}`(`outer` 없음, `inner` 있음) | `inner`의 값으로 정상 해석됨 - 재귀적으로 기본값 안의 플레이스홀더까지 풀림 |

**직접 겪은 것**: 세 번째 실험(위치만 바꿔서 승자를 뒤집는 것)을 설계하면서, `MutablePropertySources`가 `remove()`를 지원한다는 걸 미리 확인해 둬야 했다 - 처음엔 그냥 새 `StandardEnvironment`를 다시 만들어서 순서만 다르게 추가하려 했는데, 그러면 "같은 객체의 위치를 바꾼 것"이 아니라 "완전히 새로운 시나리오"가 되어 정확히 무엇을 증명하는지가 흐려질 뻔했다. `remove("a")` 후 다시 `addLast`로 넣는 방식으로 바꾸고 나서야, 같은 두 값이 위치만 바뀌었을 때 결과가 어떻게 달라지는지를 정확히 보여주는 테스트가 됐다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `StandardEnvironment#customizePropertySources`의 실제 소스: `propertySources.addLast(new PropertiesPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, ...))`가 `SystemEnvironmentPropertySource` 등록보다 코드상 먼저 온다는 것을 확인했다 - 빈 목록에 순서대로 `addLast`하면 먼저 추가한 것이 앞자리를 차지하므로, "시스템 프로퍼티가 환경 변수보다 우선한다"는 레퍼런스 문서의 문장이 정확히 이 두 줄의 순서에서 나온다.
- `PropertySourcesPropertyResolver#getProperty`의 실제 소스: `for (PropertySource<?> propertySource : this.propertySources)` 루프가 첫 번째로 `null`이 아닌 값을 찾는 순간 즉시 반환한다는 것을 확인했다 - "위치가 곧 우선순위"라는 관찰의 직접적인 근거다. 순회 자체가 `MutablePropertySources`의 이터레이션 순서(내부 `CopyOnWriteArrayList`)를 그대로 따른다.
- `PropertyPlaceholderHelper#parseStringValue`의 실제 소스(신형 `PlaceholderParser` 경유)가 플레이스홀더의 기본값 부분을 파싱할 때 그 부분 자체에서 또 다른 `${` 시작을 인식하면 재귀적으로 먼저 해석한다는 것을 확인했다 - 별도의 특수 처리가 아니라, "문자열 안에서 플레이스홀더를 찾아 치환한다"는 같은 알고리즘이 기본값 부분에도 똑같이 적용되는 자연스러운 결과였다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `PropertySource` 목록을 순서대로 순회하며 첫 매치를 채택하는 알고리즘 자체는 몇 줄짜리 `for` 루프라 재구현할 만한 복잡도가 없다 - 이번 주의 가치는 그 알고리즘이 `StandardEnvironment`의 기본 구성(시스템 프로퍼티 우선)과 `MutablePropertySources`의 위치 조작 API(`addFirst`/`addBefore` 등)를 통해 실제로 어떤 순서를 만들어 내는지를 직접 조작하고 관찰하는 데 있었다.

## 11. Spring 설계 의도

- **왜 우선순위가 "언제 추가했는가"가 아니라 "어디에 있는가"로 정의되는가**: 만약 "먼저 추가한 것이 이긴다"는 규칙이었다면, 이미 등록된 `PropertySource`의 우선순위를 나중에 조정하고 싶을 때(예: 특정 설정을 다른 것보다 우선시키고 싶어졌을 때) 그 `PropertySource`를 제거했다가 다시 만들어서 추가하는 수밖에 없다. 위치 기반 모델(`MutablePropertySources`가 리스트라는 것)은 `addFirst`/`addBefore` 같은 조작만으로 언제든 우선순위를 재배치할 수 있게 해 준다 - "추가된 시점"이라는, 되돌릴 수 없는 사실 대신 "현재 목록에서의 위치"라는, 언제든 바꿀 수 있는 상태에 우선순위를 묶어 둔 것이다.
- **왜 시스템 프로퍼티가 기본적으로 가장 앞자리인가**: 시스템 프로퍼티(`-Dkey=value`)는 애플리케이션을 **실행하는 사람**이 그 순간에 명시적으로 지정하는 값이다 - 코드 안에 있는 설정 파일이나 환경 변수(운영체제/컨테이너 수준에서 미리 정해진 것)보다 훨씬 더 "지금, 의도적으로" 준 값이라는 뜻이다. 가장 구체적이고 가장 최근에 명시된 의도를 가장 먼저 존중하는 것은, 이 저장소가 반복해서 봐 온 "명시적으로 요청한 것이 암묵적 기본값을 이긴다"는 원칙의 또 다른 사례다.
- **왜 플레이스홀더 기본값이 재귀적으로 해석되는가**: `${key:default}` 문법의 목적 자체가 "이 값을 못 찾으면 대신 이걸 써라"이다 - 그 "대신 쓸 값"도 결국 하나의 문자열이고, 그 문자열 안에 또 다른 플레이스홀더가 있다는 것은 "이 기본값 자체도 다른 설정에 따라 달라질 수 있다"는 아주 자연스러운 요구다. 별도의 특별한 문법이나 예외 규칙을 만드는 대신, "문자열을 파싱해서 플레이스홀더를 치환한다"는 같은 알고리즘을 기본값 부분에도 그대로 재귀 적용하는 것은 이 저장소가 반복해서 강조해 온 "특별 취급을 최소화한다"는 원칙과 일치한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 우선순위가 "값" 자체나 "추가된 시점"이 아니라 순수하게 "목록 안의 자리"라는 것 - 같은 두 `PropertySource`의 위치만 바꿔서 승자를 뒤집어 본 것이, 이 원칙을 가장 분명하게 보여줬다. `Environment`를 "여러 설정 소스 중 뭔가 똑똑하게 병합해 주는 것"으로 막연히 생각했었는데, 실제로는 "정렬된 리스트를 순서대로 훑어 첫 매치를 채택한다"는, 훨씬 단순하고 예측 가능한 규칙이었다.
- 예상 밖이었던 것: 중첩 플레이스홀더가 별다른 특수 처리 없이 자연스럽게 재귀적으로 해석된다는 것 - "지원되긴 하는데 뭔가 제약이 있지 않을까"라고 예상했는데, 실제로는 그냥 같은 파싱 알고리즘이 스스로를 다시 부르는 것뿐이었다.
- 예상대로였던 것(재확인): 시스템 프로퍼티가 환경 변수보다, 그리고 (기본적으로) 애플리케이션이 추가한 설정보다 우선한다는 것 - 30번(ConversionService)·34번(@Profile)에서 이미 `Environment`를 다뤄 봤기 때문에 이 결론 자체는 놀랍지 않았지만, 그 근거가 "코드 두 줄의 호출 순서"라는 것까지 확인한 건 이번이 처음이었다.
- 새로 배운 것: `MutablePropertySources`의 위치 조작 API(`addFirst`/`addLast`/`addBefore`/`addAfter`)가 단순한 편의 메서드가 아니라, 이 시리즈 전체가 의존해 온 "우선순위 = 위치"라는 모델을 실제로 조작하는 유일한 방법이라는 것 - `@PropertySource`나 Boot의 다양한 설정 소스들이 서로 다른 우선순위를 갖는 이유도, 결국 각자가 이 API의 어떤 메서드로 자신을 등록하느냐에 달려 있다는 걸 알게 됐다.
