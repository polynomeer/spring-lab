# @Profile — "default" 프로파일은 예약어가 아니라 그냥 빈 Set 하나의 기본값이다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`33`](../33-object-provider/object-provider.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 19주차(조건부 설정)는 `@ConditionalOnClass`/`@ConditionalOnMissingBean`/`@ConditionalOnProperty`를 다뤘지만, Spring Framework 자신이 `@Conditional`로 구현한 가장 오래되고 흔한 사례인 `@Profile`은 다루지 않았다 - 그 빈틈을 채운다. 그리고 "`default` 프로파일"이라는, 마치 특별한 예약어처럼 보이는 이름이 실제로는 얼마나 평범한 설정값인지를 소스로 확인한다.

## 1. 이번 질문

- `@Profile`은 정확히 `@Conditional`의 어떤 구현체로 동작하는가?
- `@Profile({"a", "b"})`처럼 여러 값을 주면 AND인가 OR인가? `@Profile("a & b")`처럼 한 문자열 안에 연산자를 쓰면 어떻게 다른가?
- "활성 프로파일이 하나도 없다"는 것과 "`default`라는 이름의 프로파일이 활성이다"는 것은 같은 개념인가?
- 프로파일 조건에 안 맞는 빈은 "등록됐지만 조회가 안 되는" 것인가, "애초에 등록되지 않는" 것인가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Bean Definition Profiles")는 `@Profile`이 특정 프로파일이 활성일 때만 그 컴포넌트/설정을 등록한다고 설명하고, `!`/`&`/`|` 연산자를 지원하는 프로파일 표현식을 소개한다.
- 문서는 "활성 프로파일이 없으면 기본 프로파일(`default`)이 활성인 것으로 취급된다"고 설명하지만, 그 "취급"이 정확히 무엇을 비교하는 것인지(문자열 `"default"`를 실제로 활성 프로파일 집합에 넣는 것인지, 아니면 별도 로직으로 처리하는 것인지)는 명시하지 않는다 — 이번 실험은 소스로 그 정확한 메커니즘을 확인했다.
- `@Profile`이 `@Conditional`의 특수 사례라는 것도 API 문서(Javadoc)에 나오지만, 19주차 문서는 그 구체적인 `Condition` 구현체(`ProfileCondition`)까지는 다루지 않았었다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `default`가 Spring 내부에 하드코딩된 특별한 프로파일 이름일 거라 예상했다(문서에서 자꾸 "기본 프로파일"이라고 강조하니) — **틀렸다.** `"default"`는 그냥 `AbstractEnvironment.RESERVED_DEFAULT_PROFILE_NAME`(또는 `spring.profiles.default` 프로퍼티)로 설정할 수 있는 평범한 문자열이고, `isProfileActive()`가 "활성 프로파일 집합이 비어 있을 때만" 이 기본 집합을 대신 확인하는 것뿐이다.
- 활성 프로파일이 하나라도 있으면(그 프로파일이 `default`와 전혀 무관해도) `@Profile("default")` 빈은 여전히 등록될 수도 있을 거라 예상했다 — **틀렸다.** 활성 프로파일이 하나라도 있는 순간, `default` 폴백 로직 자체가 완전히 비활성화된다 - `default`는 더 이상 어떤 의미도 갖지 않는다.
- `@Profile("prod & cloud")`가 `@Profile({"prod", "cloud"})`와 같은 의미(둘 다 있어야 함)일 거라 예상했다 — **틀렸다.** `@Profile({"prod", "cloud"})`처럼 배열에 여러 값을 주는 것은 **OR**(`ProfileCondition`이 배열의 각 원소를 순회하며 하나라도 맞으면 즉시 `true`)이고, 한 문자열 안의 `&`만 **AND**로 파싱된다 - 겉보기엔 비슷해 보이는 두 문법이 정반대 의미다.
- 조건에 안 맞는 빈은 컨테이너에 존재는 하되 "비활성" 상태로 표시되고, `getBean()`을 하면 예외가 날 거라 예상했다 — **틀렸다.** `BeanDefinition` 자체가 등록되지 않는다 - `containsBeanDefinition()`도 `false`를 돌려준다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/profile-condition-lab`](../../experiments/profile-condition-lab)

```java
@Component @Profile("dev")          public class DevNotifier implements Notifier { ... }
@Component @Profile("prod")         public class ProdNotifier implements Notifier { ... }
@Component @Profile("default")      public class DefaultNotifier implements Notifier { ... }
@Component @Profile("prod & cloud") public class ProdCloudNotifier implements Notifier { ... }
@Component @Profile("!prod")        public class NotProdNotifier implements Notifier { ... }
@Component                          public class AlwaysOnNotifier implements Notifier { ... }
```

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
context.getEnvironment().setActiveProfiles("dev");   // refresh() 전에 미리 설정
context.register(ProfileScanConfig.class);
context.refresh();
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `@Profile` | `@Conditional(ProfileCondition.class)`를 메타 애노테이션으로 갖는 편의 애노테이션 |
| `ProfileCondition` | `Condition` 구현체 - `@Profile`의 `value()` 배열을 순회하며 `Environment#matchesProfiles()`로 위임 |
| `Environment#matchesProfiles(String...)` | `acceptsProfiles(Profiles.of(profileExpressions))`의 축약형 - 각 문자열을 복합 불리언 표현식으로 파싱 |
| `Profiles#of(String...)` | `!`/`&`/`\|` 연산자를 지원하는 프로파일 표현식 파서 - 반환된 `Profiles` 인스턴스의 `matches()`가 실제 판정 로직 |
| `AbstractEnvironment#isProfileActive` | 활성 프로파일 집합에 있으면 true, **비어 있을 때만** 기본 프로파일 집합을 대신 확인 |
| `AbstractEnvironment#doGetDefaultProfiles` | 기본 프로파일 집합 - 아무것도 설정 안 하면 `{"default"}` |
| (실험) `Notifier` 구현체 6종 | 서로 다른 `@Profile` 표현식(단순/부정/복합/무조건)을 나란히 등록해서 각각 언제 활성화되는지 대비 |

## 6. 호출 흐름

```text
컴포넌트 스캔 (7주차) / @Configuration 처리 (8주차)
  → 후보 클래스마다 ConditionEvaluator#shouldSkip() 호출 (19주차의 @Conditional 평가 지점)
      → @Profile 발견 → ProfileCondition#matches(context, metadata)
          → metadata.getAllAnnotationAttributes("...Profile").get("value")  ("dev" 같은 String[])
          → for (Object value : ...) {
                if (context.getEnvironment().matchesProfiles((String[]) value)) return true;
             }
             return false;                                    ← 여러 @Profile이 있으면 OR
      → matchesProfiles(profiles) = acceptsProfiles(Profiles.of(profiles))
          → Profiles.of("prod & cloud")가 AND 표현식 트리로 파싱
          → matches(this::isProfileActive)
              → isProfileActive("prod") = activeProfiles.contains("prod")
                    || (activeProfiles.isEmpty() && defaultProfiles.contains("prod"))
              → isProfileActive("cloud") = 동일 로직
              → 둘 다 true여야 AND 전체가 true
  → shouldSkip()이 true를 반환하면 → 이 BeanDefinition을 레지스트리에 아예 등록하지 않음
```

`@Profile({"a","b"})`(OR)와 `@Profile("a & b")`(AND)가 갈리는 지점, 그리고 `default` 폴백이 활성 프로파일 유무 하나로 켜지고 꺼지는 것을 함께 그린 다이어그램: [`diagrams/profile-matching-flow.md`](diagrams/profile-matching-flow.md)

## 7. 브레이크포인트

25~33번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 빈 정의가 등록되는가"라는 결과였고, 그건 `containsBeanDefinition()`으로 직접 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.context.annotation.Profile (메타 애노테이션 선언부)
org.springframework.context.annotation.ProfileCondition#matches
org.springframework.core.env.Environment#matchesProfiles
org.springframework.core.env.Profiles#of
org.springframework.core.env.AbstractEnvironment#isProfileActive
org.springframework.core.env.AbstractEnvironment#doGetDefaultProfiles
```

## 8. 런타임 관찰

[`ProfileConditionTest`](../../experiments/profile-condition-lab/src/test/java/lab/experiments/profile/ProfileConditionTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| 활성 프로파일 없음 | `default`, `!prod`(→ not-prod), 무조건(`always`) 등록 - `dev`/`prod`/`prod & cloud`는 제외 |
| 활성 프로파일 `dev` 하나 | `dev`, `!prod`(→ not-prod), `always` 등록 - `default`는 **사라짐**(prod와 무관한데도) |
| 활성 프로파일 `prod` + `cloud` | `prod`, `prod & cloud`, `always` 등록 - `!prod`는 제외 |
| 활성 프로파일 `prod`만(`cloud` 없음) | `prod`, `always`는 등록되지만 `prod & cloud`는 **제외**(부분 일치 허용 안 함), `!prod`도 제외 |
| `@Profile` 없는 빈(`always`) | 활성 프로파일 조합과 무관하게 항상 등록 |
| `dev` 활성 상태에서 `prodNotifier`의 `BeanDefinition` 존재 여부 | `containsBeanDefinition("prodNotifier")`가 `false` - 빈 정의 자체가 없음 |

**직접 겪은 것**: 두 번째 행(`dev` 활성 시 `default` 소멸)은 처음엔 "activeProfiles가 있어도 `default`는 `prod`처럼 명시적으로 배제된 것만 빠지지 않을까"라고 막연히 생각했는데, 실제로는 `dev`와 `default`가 서로 아무 관계가 없는데도 `default`가 통째로 사라졌다. `isProfileActive()`의 소스(9번 절)를 보고 나서야 "`default`가 활성인지"를 판단하는 로직 자체가 **활성 프로파일 집합이 비어 있는가**라는 조건 하나에 완전히 게이트돼 있다는 것을 확인했다 - `default`라는 이름이 특별해서가 아니라, 그 이름을 확인하는 코드 경로 자체가 "다른 활성 프로파일이 전혀 없을 때만" 실행되기 때문이다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `ProfileCondition#matches`의 실제 소스: `metadata.getAllAnnotationAttributes(...).get("value")`로 얻은 여러 `String[]`을 순회하며 **첫 매치에서 즉시 `true`를 반환**한다는 것을 확인했다 - `@Profile`을 (드물지만) 여러 번 메타-부여할 수 있는 경우, 그 사이는 OR라는 근거다. 그리고 각 `String[]` 자체(하나의 `@Profile` 값)는 `matchesProfiles()` 한 번에 통째로 넘겨진다는 것도 함께 확인했다.
- `Environment#matchesProfiles`의 실제 소스: `acceptsProfiles(Profiles.of(profileExpressions))`의 단순 위임이라는 것을 확인했다 - `Profiles.of()`의 Javadoc이 `!`/`&`/`|` 연산자와 괄호를 지원하는 완전한 불리언 표현식 문법을 명시한다. `@Profile({"prod","cloud"})`(배열 - OR)와 `@Profile("prod & cloud")`(한 문자열 - AND)의 차이가 여기서 나온다.
- `AbstractEnvironment#isProfileActive`의 실제 소스: `currentActiveProfiles.contains(profile) || (currentActiveProfiles.isEmpty() && doGetDefaultProfiles().contains(profile))`를 그대로 확인했다 - `&&`의 두 번째 항 앞에 `isEmpty()` 검사가 있다는 것 자체가, "활성 프로파일이 하나라도 있으면 기본 프로파일은 아예 고려조차 안 한다"는 8번 절 관찰의 정확한 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `@Conditional` 자체의 골격(`Condition` 인터페이스, `ConditionEvaluator`가 빈 정의 등록 전에 걸러낸다)은 19주차에서 이미 다뤘고, `@Profile`은 그 골격 위에 얹힌 **하나의 구체적인 `Condition` 구현체**일 뿐이다 - 이번 주의 가치는 새 골격이 아니라, `Profiles.of()`의 표현식 문법과 `default` 폴백의 정확한 발동 조건이라는 세부 규칙에 있었다.

## 11. Spring 설계 의도

- **왜 배열(OR)과 문자열 안 연산자(AND)라는, 겉보기에 헷갈리는 두 문법을 함께 두는가**: `@Profile({"dev", "test"})`처럼 "이 중 아무거나 활성이면 된다"는 요구가 훨씬 흔하다 - 그래서 애노테이션의 기본 형태(배열)를 그 흔한 경우에 맞춰 가장 짧게 쓸 수 있게 했다. 반면 AND나 부정처럼 더 복잡한 조합은 상대적으로 드물지만 여전히 필요하므로, 그걸 위해 애노테이션 자체의 문법을 더 복잡하게 만드는 대신(예: `@Profile(all = {...}, any = {...})` 같은 것) 문자열 하나에 표현식 언어를 얹는 쪽을 택했다 - 흔한 경우는 문법으로, 드문 경우는 그 문법 안의 확장된 언어로 표현하게 한 것이다.
- **왜 `default` 폴백은 "활성 프로파일이 하나도 없을 때만" 적용되는가**: `default` 프로파일의 존재 이유는 "프로파일을 아예 설정하지 않은 개발자에게도 뭔가 합리적인 기본 빈 구성을 보장한다"는 것이다 - 그런데 개발자가 이미 명시적으로 어떤 프로파일이든 활성화했다면, 그건 "나는 프로파일이라는 개념을 이미 쓰고 있고, 내가 지정한 것만 원한다"는 분명한 의사 표현이다. 이 시점에 `default`까지 몰래 끼어들면, "내가 지정한 프로파일 + 알 수 없는 기본값"이라는 예측 불가능한 조합이 생긴다. `isEmpty()` 검사 하나로 이 두 상황(아무 설정 안 함 vs 명시적으로 뭔가 설정함)을 깔끔하게 가르는 것은, "명시적 설정이 있으면 그걸 전적으로 신뢰하고, 없을 때만 안전망을 편다"는 설계다.
- **왜 조건에 안 맞는 빈은 "등록 후 숨김"이 아니라 "애초에 미등록"인가**: 19주차에서 이미 확인했듯, `@Conditional` 평가는 `BeanDefinition`을 레지스트리에 넣기 **직전**에 일어난다 - 이 시점 이후로는 그 클래스가 존재했다는 흔적조차 남기지 않는 편이 여러모로 유리하다. 만약 "등록은 하되 비활성 표시만 해 둔다"는 방식이었다면, `getBeansOfType()` 같은 조회가 매번 그 표시를 추가로 확인해야 하고, 비활성 빈에 대한 의존성 주입 시도가 별도의 예외 처리를 필요로 했을 것이다. "아예 없었던 것처럼" 만드는 쪽이, 나머지 컨테이너 로직 전체를 프로파일이라는 개념 자체를 몰라도 되게 단순하게 유지해 준다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Profile({"a","b"})`(OR)와 `@Profile("a & b")`(AND)가 정반대 의미라는 것 - 둘 다 "a와 b를 함께 적었다"는 표면적 유사성 때문에 처음엔 같은 의도로 오해하기 쉬웠다. 배열이냐 문자열 안 연산자냐라는, 문법적으로 아주 미묘한 차이가 완전히 다른 논리 연산으로 이어진다는 걸 실행으로 직접 재현하고 나서야 확실히 구분하게 됐다.
- 예상 밖이었던 것: `default`가 활성 프로파일의 유무 하나에 완전히 게이트된 폴백일 뿐, `dev`나 `prod`와 같은 층위의 "진짜" 프로파일이 아니라는 것 - 어떤 프로파일을 활성화하든(그게 `default`와 아무 관계 없어도) `default` 관련 빈은 즉시 사라진다는 것이 직관과 가장 크게 어긋났다.
- 예상대로였던 것(재확인): 조건에 안 맞는 빈은 `BeanDefinition` 자체가 없다는 것 - 19주차에서 배운 `@Conditional`의 "등록 전 평가"라는 원칙이 `@Profile`에서도 그대로 확인됐다.
- 새로 배운 것: "예약어처럼 보이는 이름"이 실제로는 그냥 평범한 설정값의 기본값일 뿐인 경우가 있다는 것 - `default`는 코드 어디에도 특별 취급되는 문자열 상수로 하드코딩돼 있지 않고, `doGetDefaultProfiles()`가 반환하는 `Set<String>`의 초기값일 뿐이다. 25~33번을 거치며 "이름이 계약이다"(`"conversionService"`, 30번)라는 패턴을 이미 봤는데, 이번엔 그 반대 - "특별해 보이는 이름이 사실은 평범한 기본값"이라는 패턴을 새로 확인했다.
