# MessageSource — "messageSource"라는 이름이 다시 한번 계약이 된다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`38`](../38-resource-abstraction/resource-abstraction.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 3주차(`ApplicationContext.refresh()`)의 12단계 중 `initMessageSource()`는 이름만 나열됐을 뿐 한 번도 깊이 들여다본 적이 없다 - 이번 문서에서 직접 확인한다. 그리고 [`30-conversion-service`](../30-conversion-service/conversion-service.md)에서 본 "정확한 빈 이름 하나가 계약이다"라는 패턴이, `MessageSource`에서도 토씨 하나 다르지 않게 반복된다는 것도 함께 확인한다.

## 1. 이번 질문

- `MessageSource` 빈을 어떻게 등록해야 `context.getMessage()`가 그걸 실제로 쓰는가?
- 요청한 로케일에 맞는 번들이 없으면 어떻게 되는가?
- 코드를 찾을 수 없을 때 예외가 나는 경우와 안 나는 경우는 무엇이 다른가?
- 부모-자식 컨텍스트 구조에서, 자식에 `MessageSource`가 없으면 부모 것을 대신 쓸 수 있는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Internationalization using MessageSource")는 `ResourceBundleMessageSource`/`ReloadableResourceBundleMessageSource`를 `messageSource`라는 이름의 빈으로 등록하면 국제화 메시지 조회가 가능해진다고 설명한다.
- 문서는 "빈 이름이 `messageSource`여야 한다"고 명시하지만, 이름이 다르면 정확히 어떤 일이 일어나는지(예외인지, 조용한 무시인지)는 다루지 않는다 — 30번 문서가 `ConversionService`에서 이미 겪었던 바로 그 질문이다.
- `getMessage(code, args, locale)`(예외를 던짐)과 `getMessage(code, args, defaultMessage, locale)`(기본값으로 완화됨) 두 오버로드가 있다는 것은 API 문서에 나오지만, 로케일 폴백 규칙(요청한 로케일이 없으면 어디로 떨어지는지)은 `ResourceBundle`(자바 표준 라이브러리)의 규칙을 그대로 물려받는다는 것까지는 명시하지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `MessageSource` 타입의 빈을 아무 이름으로나 등록해도 컨텍스트가 타입으로 찾아 자동 연결해 줄 거라 예상했다 — **틀렸다.** 정확히 `"messageSource"`라는 리터럴 이름의 빈만 찾는다(`AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME`) - 30번의 `"conversionService"`와 완전히 같은 패턴이다.
- 이름이 틀린 `MessageSource` 빈이 있으면, `getMessage()`를 부를 때 최소한 예외 메시지 정도는 "빈이 있긴 한데 이름이 이상하다"는 힌트를 줄 거라 예상했다 — **틀렸다.** 그런 빈은 컨테이너 입장에서 아예 존재하지 않는 것과 동일하게 취급된다 - 조용히 빈(empty) `DelegatingMessageSource`로 폴백해서, 실제로 해석 가능한 메시지조차 전부 `NoSuchMessageException`으로 실패한다.
- 지원하지 않는 로케일을 요청하면 예외가 나거나 `null`이 돌아올 거라 예상했다 — **틀렸다.** 자바 표준 `ResourceBundle`의 폴백 체인을 그대로 따라 언어 접미사가 없는 기본 번들로 자연스럽게 떨어진다 - 별도의 에러 처리가 전혀 필요 없다.
- 자식 컨텍스트에 `MessageSource`가 없으면 조회가 아예 실패할 거라 예상했다 — **틀렸다.** 자식의 빈(empty) `DelegatingMessageSource`가 부모 컨텍스트의 `MessageSource`를 자동으로 연결받아서, 자식이 직접 아무것도 등록하지 않아도 부모의 메시지를 그대로 쓸 수 있다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/message-source-lab`](../../experiments/message-source-lab)

```java
@Configuration
public class CorrectlyNamedMessageSourceConfig {
    @Bean
    public MessageSource messageSource() {                 // 이름이 정확히 "messageSource"
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");                    // messages.properties / messages_ko.properties
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
```

```java
context.getMessage("greeting.hello", new Object[]{"Spring"}, Locale.KOREAN);
// → "안녕하세요, Spring님!"

context.getMessage("greeting.hello", new Object[]{"Spring"}, Locale.FRENCH);
// → "Hello, Spring!"  (messages_fr.properties가 없어서 기본 번들로 폴백)

context.getMessage("no.such.code", null, Locale.ENGLISH);
// → NoSuchMessageException

context.getMessage("no.such.code", null, "fallback default", Locale.ENGLISH);
// → "fallback default"  (예외 없음)
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME` | `"messageSource"`라는 리터럴 문자열 상수 - 30번의 `CONVERSION_SERVICE_BEAN_NAME`과 완전히 같은 역할 |
| `AbstractApplicationContext#initMessageSource` | `refresh()`의 한 단계(3주차에서 이름만 다뤘던 그 단계) - 이 이름의 빈이 있으면 그걸 쓰고, 없으면 빈 `DelegatingMessageSource`를 새로 만들어 같은 이름으로 등록 |
| `DelegatingMessageSource` | 자기 자신은 실제 메시지를 하나도 갖고 있지 않고, `parentMessageSource`가 있으면 전부 그쪽으로 위임하는 플레이스홀더 구현체 |
| `ResourceBundleMessageSource` | 자바 표준 `ResourceBundle`(→ `.properties` 파일들)을 그대로 활용하는 `MessageSource` 구현체 - 로케일 폴백도 `ResourceBundle` 표준 규칙을 그대로 물려받음 |
| `HierarchicalMessageSource` | `setParentMessageSource()`/`getParentMessageSource()` - 부모-자식 컨텍스트 구조에서 메시지 조회를 연쇄시킬 수 있게 하는 인터페이스 |
| `NoSuchMessageException` | 코드를 해석할 수 없고 기본 메시지도 없을 때 던져지는 전용 예외 |

## 6. 호출 흐름

```text
ApplicationContext.refresh()                            (3주차의 12단계)
  → initMessageSource()                                  (4번째 단계)
      → if (beanFactory.containsLocalBean("messageSource")) {
            this.messageSource = beanFactory.getBean("messageSource", MessageSource.class)
            (HierarchicalMessageSource이고 부모 MessageSource가 아직 없으면
             자동으로 hms.setParentMessageSource(부모 컨텍스트의 MessageSource) 연결)
        } else {
            DelegatingMessageSource dms = new DelegatingMessageSource()
            dms.setParentMessageSource(부모 컨텍스트의 MessageSource 또는 null)
            this.messageSource = dms
            beanFactory.registerSingleton("messageSource", dms)   ← 이름이 다른 빈은 여기서
                                                                     전혀 고려되지 않음
        }

context.getMessage(code, args, locale)
  → this.messageSource.getMessage(code, args, locale)
      → ResourceBundleMessageSource: ResourceBundle.getBundle(basename, locale)
          → 요청 로케일에 맞는 .properties가 없으면 ResourceBundle 표준 폴백 체인을 따라
            언어 접미사가 없는 기본 번들로 떨어짐
          → 코드가 있으면 MessageFormat으로 {0} 같은 인자를 치환해서 반환
          → 코드가 없으면 NoSuchMessageException (또는 DelegatingMessageSource라면
            parentMessageSource로 그대로 위임)
```

`"messageSource"`라는 이름 하나가 실제 구현체 연결 여부를 가르는 지점과, 부모-자식 컨텍스트 사이의 위임 경로를 함께 그린 다이어그램: [`diagrams/message-source-wiring.md`](diagrams/message-source-wiring.md)

## 7. 브레이크포인트

25~38번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "메시지가 해석되는가/안 되는가"라는 결과였고, 그건 실행 결과로 확인하는 쪽이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.context.support.AbstractApplicationContext#initMessageSource
org.springframework.context.support.AbstractApplicationContext#MESSAGE_SOURCE_BEAN_NAME
org.springframework.context.support.DelegatingMessageSource#getMessage
org.springframework.context.HierarchicalMessageSource
```

## 8. 런타임 관찰

[`MessageSourceTest`](../../experiments/message-source-lab/src/test/java/lab/experiments/messagesource/MessageSourceTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| 빈 이름이 정확히 `"messageSource"` | `Locale.ENGLISH`/`Locale.KOREAN` 둘 다 정확히 해석됨(`{0}` 인자 치환 포함) |
| 지원하지 않는 로케일(`Locale.FRENCH`) | 자동으로 기본 번들(영어)로 폴백 - 별도 처리 불필요 |
| 존재하지 않는 코드, 기본 메시지 없음 | `NoSuchMessageException` |
| 존재하지 않는 코드, 기본 메시지 있음 | 예외 없이 그 기본 메시지 그대로 반환 |
| 같은 타입, 다른 이름(`myMessageSource`)의 빈 | 실제로 해석 가능한 코드(`greeting.hello`)조차 `NoSuchMessageException` - 그 빈은 컨테이너에 존재하지 않는 것과 완전히 동일하게 취급됨 |
| 자식 컨텍스트에 `messageSource` 빈이 전혀 없음, 부모에는 있음 | 자식에서 조회해도 부모의 메시지가 그대로 해석됨 |

**직접 겪은 것**: 다섯 번째 행을 설계할 때 30번(`ConversionService`)의 경험이 그대로 도움이 됐다 - "이름이 틀린 빈은 아예 없는 것과 같다"는 걸 이미 알고 있었으므로, 이번엔 그 가설을 세우고 실행으로 확인하는 순서가 훨씬 빨랐다. 다만 여섯 번째 행(부모-자식 위임)은 이번에 처음 다룬 것이라 예상이 필요했는데, "자식이 아무것도 안 하면 조회가 실패하지 않을까"라고 예상했다가 실제로는 `DelegatingMessageSource`가 부모를 자동으로 연결받는다는 걸 보고 놀랐다 - `initMessageSource()`의 else 분기(6번 절)를 다시 읽고 나서야, 빈 `DelegatingMessageSource`를 만들 때도 부모 컨텍스트의 `MessageSource`를 미리 연결해 둔다는 걸 확인했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `AbstractApplicationContext#initMessageSource`의 실제 소스: `beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)`(정확한 이름 하나만 확인)가 참이면 그 빈을 그대로 쓰고, 거짓이면 새 `DelegatingMessageSource`를 만들어 **같은 이름**으로 `registerSingleton()`한다는 것을 확인했다 - 이름이 다른 `MessageSource` 빈이 "덜 도움이 되는" 정도가 아니라 완전히 무시되는 이유이자, 30번의 `ConversionService`와 정확히 같은 구조라는 근거다.
- `DelegatingMessageSource#getMessage`의 실제 소스: `parentMessageSource != null`이면 그대로 위임하고, 아니면 기본 메시지가 있을 때만 그걸 렌더링해서 반환하며, 그마저 없으면 `null`을 반환한다는 것을 확인했다 - 여섯 번째 행(부모 위임)의 직접적인 근거다.
- `initMessageSource()`의 `if` 분기 안에서도, `this.messageSource instanceof HierarchicalMessageSource hms && hms.getParentMessageSource() == null`일 때만 부모를 자동으로 연결한다는 조건을 확인했다 - 사용자가 이미 명시적으로 부모를 설정해 둔 `MessageSource`라면(예: 직접 `setParentMessageSource()`를 호출해 둔 경우) 그 설정을 덮어쓰지 않는다는 뜻이다 - "사용자가 이미 결정한 것은 존중한다"는, 이 저장소가 반복해서 봐 온 원칙이 여기서도 나타난다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `MessageSource`의 핵심 로직(코드 → 로케일별 메시지 조회, 인자 치환)은 자바 표준 `ResourceBundle`/`MessageFormat`에 거의 그대로 위임돼 있어서 Spring이 새로 발명한 알고리즘이 없다 - 이번 주의 가치는 "`messageSource`라는 이름이 계약이다"라는, 30번에서 이미 배운 패턴이 완전히 다른 기능에서도 토씨 하나 다르지 않게 반복된다는 것을 확인하는 데 있었다. 그래서 mini 구현보다는 이름 규약과 부모-자식 위임이라는 "배선"에 집중했다.

## 11. Spring 설계 의도

- **왜 `MessageSource`도 정확한 빈 이름(타입이 아니라)으로 연결하는가**: 30번에서 이미 확인한 이유가 그대로 적용된다 - 컨텍스트 안에 `MessageSource` 타입의 빈이 여러 개 있을 수 있고(예: 특정 모듈 전용으로 좁게 만든 것), 타입만으로 "이게 전역 기본값이다"라고 단정하면 모호성 문제나 의도치 않은 승격이 생긴다. 정확한 이름을 계약으로 못 박아 두면 "이 이름을 쓰겠다"는 것 자체가 "나는 전역 기본 메시지 소스가 되고 싶다"는 명시적 의사 표현이 된다 - `ConversionService`와 `MessageSource` 둘 다 같은 문제(전역 기본값 지정의 모호성)를 같은 해법(고정된 이름)으로 풀고 있다는 뜻이다.
- **왜 빈 `DelegatingMessageSource`를 항상 만들어 두는가(이름이 맞는 빈이 없어도)**: 만약 `MessageSource` 빈이 없을 때 `this.messageSource`를 그냥 `null`로 남겨 둔다면, `context.getMessage()`를 호출하는 모든 코드가 매번 `null` 체크를 해야 한다 - "메시지 소스가 없을 수도 있다"는 예외적인 경우를 API 사용자에게 떠넘기는 것이다. 대신 항상 뭔가(빈 것이라도) 채워 두면, `getMessage()`를 부르는 쪽은 "국제화 기능이 설정돼 있는지"를 신경 쓸 필요 없이 그냥 호출하면 되고, 실패는 (기본 메시지가 없다면) `NoSuchMessageException`이라는 일관된 방식으로만 나타난다.
- **왜 부모의 `MessageSource`를 자동으로 연결해 두는가**: 계층적 컨텍스트 구조(예: 웹 애플리케이션의 루트 컨텍스트 + 서블릿별 자식 컨텍스트)에서, 국제화 메시지는 보통 애플리케이션 전체에 공통이다 - 자식 컨텍스트마다 매번 같은 메시지 소스를 다시 등록하게 하는 것은 불필요한 반복이다. `DelegatingMessageSource`가 부모를 자동으로 연결해 두는 것은, "명시적으로 등록하지 않았다면 상위 컨텍스트의 것을 물려받는다"는 계층 구조의 자연스러운 기대를 그대로 구현한 것이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 30번(`ConversionService`)에서 배운 "정확한 이름이 계약이다"라는 패턴이, 완전히 다른 기능(`MessageSource`)에서 코드 구조까지 거의 똑같이 반복된다는 것 - `containsBean("이름") && isTypeMatch(...)`(30번)와 `containsLocalBean("이름")`(이번 주)이라는 표현의 사소한 차이만 있을 뿐, "정확한 이름 하나로 전역 기본값을 지정한다"는 설계 자체는 동일했다. 서로 다른 문제(타입 변환 vs 국제화)가 같은 해법으로 수렴한다는 것을 두 번째로 확인했다.
- 예상 밖이었던 것: 부모-자식 컨텍스트 사이의 `MessageSource` 자동 연결 - `DelegatingMessageSource`를 새로 만드는 경우에도 부모를 미리 연결해 둔다는 걸 몰랐다면, "자식 컨텍스트는 반드시 자기만의 메시지 소스를 등록해야 한다"고 잘못 결론 내렸을 것이다.
- 예상대로였던 것(재확인): 로케일 폴백이 자바 표준 `ResourceBundle` 규칙을 그대로 따른다는 것 - Spring이 국제화라는 복잡해 보이는 문제에서도 새 알고리즘을 발명하기보다는 표준 라이브러리에 위임하는 쪽을 택했다.
- 새로 배운 것: 3주차부터 이름만 알고 있던 `refresh()`의 `initMessageSource()` 단계가, 사실은 30번의 `prepareBeanFactory()`(conversionService 연결)와 거의 같은 모양의 "이름 기반 옵트인" 로직을 담고 있었다는 것 - `refresh()`의 12단계 각각이 서로 무관해 보여도, 그 내부 구현 패턴은 이렇게 서로 닮아 있을 수 있다는 걸 이번에 발견했다.
