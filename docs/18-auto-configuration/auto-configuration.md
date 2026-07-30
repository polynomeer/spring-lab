# 자동 설정 — @Import 하나가 나머지 전부를 대신하는 방법

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 18주차(선택 과정: Spring Boot 내부, 2주차)에 대응하는 분석 문서다. 카탈로그에 이 주차 전용 프로젝트 번호가 없어서, 로드맵의 핵심 질문에 맞춰 [`experiments/auto-configuration-lab`](../../experiments/auto-configuration-lab)을 직접 설계했다.

## 1. 이번 질문

- 자동 설정 후보는 어디에서 읽는가?
- 사용자 설정이 있으면 자동 설정이 물러나는 이유는 무엇인가 — 단지 `@ConditionalOnMissingBean`이 있어서라고 하기엔, "이미 등록된 빈이 있는지"를 **언제** 확인하는지가 더 중요한 질문이다.
- 자동 설정 순서는 어떻게 정해지는가?

## 2. 공식 문서 요약

- Spring Boot 레퍼런스("Auto-configuration")는 `@EnableAutoConfiguration`이 클래스패스에 있는 후보들을 "자동으로" 설정한다고 설명하고, Spring Boot 2.7부터 후보 목록을 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 파일에 클래스 이름을 한 줄씩 적어 등록한다고 명시한다(이전에는 `spring.factories`에 `EnableAutoConfiguration` 키로 등록했다).
- 같은 문서는 자동 설정이 "항상 사용자 정의 빈보다 나중에 고려된다"고 설명하고, `@AutoConfiguration(before = ..., after = ...)`로 자동 설정끼리의 순서를 지정할 수 있다고 안내한다.
- 정확히 **어떻게** "나중에" 고려되는지(타이밍의 실체)는 `DeferredImportSelector`라는 Spring Framework 코어 개념까지 내려가야 확인할 수 있었다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 자동 설정 후보 목록이 `spring.factories`(리플렉션/프로퍼티 파일 파싱)처럼 다소 무거운 메커니즘일 거라 예상했다 — 실제로는 그냥 **한 줄에 클래스 이름 하나씩** 적힌 텍스트 파일을 `BufferedReader`로 읽는, 놀랄 만큼 단순한 코드였다(`ImportCandidates.load()`).
- `@ConditionalOnMissingBean`이 "지금까지 등록된 빈을 확인"한다는 것은 알고 있었지만, 그 "지금까지"가 정확히 언제인지는 막연했다 — 소스를 보니 일반 `@Import`와 완전히 다른 시점(모든 `@Configuration` 클래스 파싱이 끝난 뒤)에 처리되는 별도의 메커니즘(`DeferredImportSelector`)이 있었다.
- `.imports` 파일에 적힌 순서가 곧 처리 순서일 거라 예상했다 — **틀렸다.** `@AutoConfiguration(after = ...)` 선언이 파일에 적힌 순서를 완전히 무시하고 재정렬한다는 것을 직접 파일 순서를 뒤바꿔서 확인했다.

## 4. 최소 재현 코드

**실제 Spring Boot** — [`experiments/auto-configuration-lab`](../../experiments/auto-configuration-lab)
```java
@AutoConfiguration(after = LoggingSupportAutoConfiguration.class)
@ConditionalOnProperty(prefix = "greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GreetingService.class)
    public GreetingService greetingService() {
        return new DefaultGreetingService();
    }
}
```
```text
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# 일부러 순서를 뒤바꿔 적었다
lab.experiments.autoconfig.GreetingAutoConfiguration
lab.experiments.autoconfig.LoggingSupportAutoConfiguration
```
```java
@Configuration
@EnableAutoConfiguration   // SpringApplication 없이, 순수 AnnotationConfigApplicationContext로도 동작한다
public class PlainAppConfig {
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AutoConfigurationImportSelector` | `@EnableAutoConfiguration`이 실제로 `@Import`하는 셀렉터 - `DeferredImportSelector`를 구현 |
| `ImportCandidates` | `META-INF/spring/{애노테이션 FQCN}.imports` 파일을 읽어 후보 클래스 이름 목록을 반환 |
| `AutoConfigurationSorter` | `@AutoConfiguration(before/after)` 메타데이터로 후보들을 위상 정렬(topological sort) |
| `DeferredImportSelector`(Spring Framework 코어) | 일반 `@Import`와 달리, **모든 `@Configuration` 클래스 파싱이 끝난 뒤** 별도로 처리되는 임포트 셀렉터 |
| `ConfigurationClassParser.DeferredImportSelectorHandler` | `deferredImportSelectors` 목록에 일단 모아 뒀다가, `process()`에서 한꺼번에 처리 |
| `ConditionEvaluationReport` | 각 자동 설정 후보가 왜 매치/불일치했는지 이유를 기록(19주차에서 자세히 다룸) |

## 6. 호출 흐름

```text
@EnableAutoConfiguration
  → @Import(AutoConfigurationImportSelector.class)   (일반 @Import처럼 보이지만...)
      → ConfigurationClassParser가 이 셀렉터를 발견 - DeferredImportSelector이므로 즉시 처리하지
        않고 DeferredImportSelectorHandler.deferredImportSelectors에 등록만 해 둠
      → (그 사이 사용자의 @Configuration/@Bean 전부가 먼저 파싱·등록됨)
      → 파싱이 전부 끝난 뒤 DeferredImportSelectorHandler.process() 호출
          → AutoConfigurationImportSelector#selectImports (Group 방식으로 실제 처리)
              → getCandidateConfigurations()
                  → ImportCandidates.load(AutoConfiguration.class, classLoader)
                      → classLoader.getResources("META-INF/spring/....AutoConfiguration.imports")
                      → 한 줄씩 읽어 후보 클래스 이름 목록 구성
              → AutoConfigurationSorter로 후보 재정렬 (before/after 위상 정렬)
              → 각 후보에 @ConditionalOnClass/@ConditionalOnMissingBean/... 평가
                  (이 시점에는 사용자가 등록한 모든 빈이 이미 BeanDefinition으로 존재하므로
                   @ConditionalOnMissingBean이 정확하게 "사용자가 이미 등록했는가"를 판단할 수 있음)
              → 조건을 통과한 후보만 실제로 @Import 처리(BeanDefinition 등록)
```

`DeferredImportSelector`의 지연 처리 타이밍과 `AutoConfigurationSorter`의 재정렬을 함께 그린 다이어그램: [`diagrams/auto-configuration-flow.md`](diagrams/auto-configuration-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.context.annotation.ConfigurationClassParser$DeferredImportSelectorHandler#process
org.springframework.boot.autoconfigure.AutoConfigurationImportSelector#getCandidateConfigurations
org.springframework.boot.context.annotation.ImportCandidates#load
org.springframework.boot.autoconfigure.AutoConfigurationSorter#sortByAnnotation
```

## 8. 런타임 관찰

[`AutoConfigurationDiscoveryTest`](../../experiments/auto-configuration-lab/src/test/java/lab/experiments/autoconfig/AutoConfigurationDiscoveryTest.java) (3개):

| 실험 | 결과 |
| --- | --- |
| `@EnableAutoConfiguration`만 붙은 설정 클래스(컴포넌트 스캔·명시적 `@Import` 없음) | `.imports` 파일의 후보가 전부 자동으로 발견돼 빈으로 등록됨 |
| `.imports` 파일에는 `GreetingAutoConfiguration`이 먼저, `@AutoConfiguration(after=...)`는 반대로 지정 | 실제 `BeanDefinition` 등록 순서(`getBeanDefinitionNames()`)는 파일 순서를 무시하고 `after` 선언을 따름 - 서로 의존관계가 없는데도 |
| 사용자가 `GreetingService` 빈을 직접 등록 | `GreetingAutoConfiguration`이 물러나고 사용자 빈이 그대로 유지됨 |

**직접 확인한 것**: `@EnableAutoConfiguration`은 `SpringApplication.run()` 없이 평범한 `AnnotationConfigApplicationContext`로 구동해도 동일하게 동작했다 - Spring Boot가 특별한 부트스트랩 마법을 부리는 게 아니라, `@Import`라는 Spring Framework 코어 메커니즘(그것도 그중에서도 `DeferredImportSelector`라는 이미 존재하던 확장점) 위에 자동 설정 후보 탐색 로직을 얹은 것뿐이라는 것을 재확인했다.

## 9. 공식 테스트 분석

이번 주도 `spring-boot`/`spring-boot-autoconfigure`의 공식 테스트 코드는 직접 열람하지 못했다(테스트 소스가 별도 배포되지 않는다, 17주차와 동일) - 정직하게 밝혀 둔다. 대신 두 가지 실제 릴리스 소스로 직접 확인했다.

- `AutoConfigurationImportSelector`가 `DeferredImportSelector`(Spring Framework 코어 인터페이스, `spring-context`)를 구현한다는 것을 소스에서 직접 확인했다.
- `ConfigurationClassParser.DeferredImportSelectorHandler#handle()`의 자바독 주석("If deferred import selectors are being collected, this registers this instance to the list")과 실제 구현이 정확히 일치한다는 것을 확인했다 - `handle()`이 즉시 처리하지 않고 리스트에 쌓아만 두는 것, 그리고 `process()`가 파싱 전체가 끝난 뒤 한 번에 처리하는 것이다.
- 6주차 문서에서 이미 다룬 `MergedBeanDefinitionPostProcessor`의 "재등록으로 순서를 뒤바꾸는" 메커니즘과, 이번 주 `AutoConfigurationSorter`의 위상 정렬은 둘 다 "선언적 순서 힌트를 별도 자료구조로 모아 뒀다가 한꺼번에 정렬한다"는 같은 종류의 설계다 - 다만 4주차는 우선순위 재배치, 이번 주는 명시적 그래프(before/after) 기반 위상 정렬이라는 차이가 있다.

## 10. 축소 구현 (이번 주는 생략)

이번 주도 mini 구현은 만들지 않았다 - 17주차와 같은 이유다. 다만 20주차(Starter와 AutoConfiguration 직접 구현)에서 우리가 직접 만드는 `mini-observability-spring-boot-starter`가 사실상 이번 주 배운 메커니즘(`.imports` 파일 + 조건부 등록)을 실전에 적용해 보는 축소 구현 역할을 겸한다.

## 11. Spring 설계 의도

- **왜 자동 설정 후보 목록이 복잡한 리플렉션이 아니라 단순 텍스트 파일인가**: 자동 설정 후보를 찾는 일은 애플리케이션이 시작될 때마다(그리고 Spring Boot는 대규모 서비스에서도 흔히 쓰이므로 자주) 반복된다. 클래스패스를 스캔하거나 애노테이션을 리플렉션으로 뒤지는 대신, 빌드 시점에 이미 정해진 후보 목록을 텍스트 파일 하나로 미리 적어 두면 시작 시점 비용이 "파일 하나 읽기"로 끝난다 - 7주차에서 확인한 "Spring은 클래스를 함부로 로딩하지 않는다"는 원칙과 같은 방향의 최적화다.
- **왜 자동 설정은 `DeferredImportSelector`라는 별도 처리 경로를 타는가**: `@ConditionalOnMissingBean`이 제대로 동작하려면 "사용자가 이미 등록한 빈이 있는가"를 판단할 시점에 사용자의 모든 명시적 등록이 이미 끝나 있어야 한다. 만약 자동 설정도 일반 `@Import`처럼 발견 즉시 처리된다면, 아직 파싱되지 않은 사용자 설정 클래스의 빈은 "없는 것"으로 잘못 판단될 위험이 있다. `DeferredImportSelector`는 정확히 이 순서 문제를 해결하기 위해 Spring Framework가 미리 마련해 둔 확장점이고, Spring Boot는 이걸 그대로 가져다 썼을 뿐이다.
- **왜 자동 설정 순서는 명시적 `before`/`after` 선언으로 강제하는가**: 자동 설정끼리는 서로의 존재를 몰라도 되도록 설계됐다(스타터를 자유롭게 조합할 수 있어야 하므로) - 그런데 어떤 자동 설정은 다른 자동 설정이 등록한 빈에 의존해야 할 수 있다. 이때 의존관계 자체를 만들면(생성자 주입 등) 순서가 자연히 강제되지만, "의존관계는 없지만 순서는 있어야 하는" 경우(예: 이번 실험처럼 어떤 조건 평가가 다른 자동 설정의 존재 여부에 따라 달라지는 경우)를 위해 `before`/`after`라는 순수한 순서 선언 수단을 별도로 제공한 것이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 자동 설정 후보 탐색이 사용자 설정 처리와 "동시에" 일어나는 게 아니라, 사용자 설정이 전부 끝난 **뒤에** `DeferredImportSelector`로 별도 처리된다는 것 - 이 타이밍 차이 하나가 `@ConditionalOnMissingBean`이 정확하게 동작할 수 있는 이유의 전부였다.
- 예상대로였던 것(재확인): `@AutoConfiguration(after=...)`가 파일 순서와 무관하게 실제 처리 순서를 결정한다는 것 - 레퍼런스 문서에 설명된 대로였다.
- 새로 배운 것: `.imports` 파일 메커니즘 자체가 놀랍도록 단순하다는 것, 그리고 이게 15~16주차에서 봤던 "확장점을 좁게 쪼갠다"는 원칙과 17주차의 `ApplicationContextFactory`(`SpringFactoriesLoader` 기반 SPI)가 사실 같은 계열의 메커니즘(다만 `spring.factories`형 SPI에서 `.imports`형 SPI로 진화한 것)이라는 것.
- 다음 주로 이어지는 질문: `@ConditionalOnClass`/`@ConditionalOnProperty`/커스텀 `Condition`이 정확히 어떻게 동작하고, 그 판정 결과가 `ConditionEvaluationReport`에 어떻게 기록되는지는 19주차(조건부 설정)로 이어진다 - 사실 이미 같은 `experiments/auto-configuration-lab` 모듈에서 코드까지는 만들어 뒀다.
