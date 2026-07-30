# 조건부 설정 — Condition 하나로 표현되는 @ConditionalOnXxx 전부

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 19주차(선택 과정: Spring Boot 내부, 3주차)에 대응하는 분석 문서다. 18주차와 같은 모듈([`experiments/auto-configuration-lab`](../../experiments/auto-configuration-lab))을 그대로 확장해서 만들었다 - 조건부 애노테이션은 자동 설정과 분리된 별개의 주제가 아니라, 18주차에서 본 "탐색된 후보를 걸러내는" 바로 그 단계이기 때문이다.

## 1. 이번 질문

- `@ConditionalOnClass`/`@ConditionalOnMissingBean`/`@ConditionalOnProperty`처럼 서로 다른 이름의 애노테이션들이 내부적으로는 얼마나 다른가 - 아니면 사실 전부 같은 메커니즘의 다른 파라미터일 뿐인가?
- 커스텀 `Condition`을 만들면 표준 조건들과 동등하게 취급되는가 - 즉 `ConditionEvaluationReport`에 똑같이 기록되는가?
- 클래스가 클래스패스에 있는지 없는지를 판단할 때, 그 클래스를 실제로 로딩해서 확인하는가(7주차에서 본 "Spring은 함부로 클래스를 로딩하지 않는다"는 원칙과 충돌하지 않는가)?

## 2. 공식 문서 요약

- Spring Boot 레퍼런스("Condition Annotations")는 `@ConditionalOnXxx` 계열 애노테이션들이 전부 Spring Framework 코어의 `@Conditional(...)`을 메타 애노테이션으로 감싼 것이라고 설명한다 - 즉 프레임워크 차원의 새로운 조건 판정 엔진이 아니라, Framework가 이미 제공하는 `Condition` 인터페이스의 구현체들일 뿐이다.
- `@ConditionalOnClass(name = "...")`처럼 문자열로 클래스 이름을 지정할 수 있는 이유에 대해 "대상 클래스가 클래스패스에 없을 수도 있는 선택적 의존성을 다룰 때 쓴다"고 설명한다 - 만약 `@ConditionalOnClass(SomeClass.class)`처럼 클래스 리터럴로 썼다면, 그 클래스가 없는 환경에서는 애노테이션을 읽는 것 자체가(어노테이션 파싱이 클래스를 로딩하려다) 실패했을 것이다.
- `--debug` 플래그나 `ConditionEvaluationReportLoggingListener`로 조건 평가 결과를 확인할 수 있다고 안내하지만, 프로그램적으로 `ConditionEvaluationReport.get(beanFactory)`를 직접 조회하는 방법까지는 자세히 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@ConditionalOnClass`가 클래스 존재를 확인하려면 실제로 그 클래스를 로딩(및 초기화)해 봐야 할 거라 예상했다 - **틀렸다.** `ClassUtils.isPresent(className, classLoader)` 방식으로, 클래스를 **로딩은 하되 초기화(static 블록 실행 등)는 하지 않는** 방식으로 존재 여부만 확인한다 - 7주차 컴포넌트 스캔에서 본 "필요 이상으로 클래스를 건드리지 않는다"는 원칙과 같은 결이다.
- 커스텀 `Condition`을 만들면 표준 조건들과는 "2등 시민"처럼 별도로 취급될 거라 예상했다 - **틀렸다.** `SpringBootCondition`을 상속하고 `ConditionOutcome`을 반환하기만 하면, `ConditionEvaluationReport`가 표준 조건과 완전히 동일한 방식으로 기록한다.
- `@ConditionalOnProperty`에 `matchIfMissing`을 안 쓰면 프로퍼티가 없을 때 기본적으로 "꺼짐"일 거라 예상했다 - 맞았다. 그런데 실제 Spring Boot의 많은 자동 설정이 `matchIfMissing = true`를 쓴다는 것은, "이 프로퍼티는 명시적으로 끄지 않는 한 켜져 있다"는 관례를 의도적으로 만든 것이었다.

## 4. 최소 재현 코드

**실제 Spring Boot** — [`experiments/auto-configuration-lab`](../../experiments/auto-configuration-lab)
```java
@AutoConfiguration
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")   // 컴파일 의존성 없이 문자열로만
public class JacksonSupportAutoConfiguration {
    @Bean
    public JacksonProbe jacksonProbe() { return new JacksonProbe(); }
}
```
```java
public class OnSlowModeCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String property = context.getEnvironment().getProperty("lab.slow-mode");
        ConditionMessage.Builder message = ConditionMessage.forCondition("OnSlowMode");
        if ("true".equals(property)) {
            return ConditionOutcome.match(message.foundExactly("lab.slow-mode=true"));
        }
        return ConditionOutcome.noMatch(message.didNotFind("lab.slow-mode=true property").atAll());
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `Condition`(Spring Framework 코어) | `matches(ConditionContext, AnnotatedTypeMetadata)` 하나만 있는 최소 인터페이스 - 모든 `@ConditionalOnXxx`의 최종 도착지 |
| `SpringBootCondition` | `Condition`을 구현하면서 `matches()` 호출을 `getMatchOutcome()`(match 여부 + 이유 메시지)으로 바꿔 주고, 로그 출력과 `ConditionEvaluationReport` 기록을 대신 처리 |
| `FilteringSpringBootCondition`/`OnClassCondition` | `@ConditionalOnClass`/`@ConditionalOnMissingClass`의 실제 구현 - `ClassNameFilter.PRESENT`/`MISSING`으로 클래스 존재 여부만 확인(로딩은 하되 초기화는 안 함) |
| `OnBeanCondition` | `@ConditionalOnBean`/`@ConditionalOnMissingBean`의 실제 구현 - 지금까지 등록된 `BeanDefinition`/타입을 조회 |
| `OnPropertyCondition` | `@ConditionalOnProperty`의 실제 구현 - `Environment`에서 프로퍼티 값을 읽어 `havingValue`/`matchIfMissing`과 비교 |
| `ConditionMessage` | "왜 매치/불일치했는지"를 사람이 읽을 문장으로 조립하는 빌더 |
| `ConditionEvaluationReport` | 소스(설정 클래스)별로 `ConditionAndOutcomes`를 모아 두는 리포트 - 표준/커스텀 조건을 구분하지 않는다 |

## 6. 호출 흐름

```text
@Conditional(OnSlowModeCondition.class)  (또는 @ConditionalOnClass/@ConditionalOnProperty 등)
  → ConfigurationClassParser가 후보를 평가할 때 conditionEvaluator.shouldSkip() 호출
      → 등록된 모든 Condition 구현체를 순서대로 matches() 호출
          → SpringBootCondition#matches()
              → getMatchOutcome(context, metadata)  (서브클래스가 구현)
                  OnClassCondition: ClassNameFilter.PRESENT/MISSING으로 클래스로더 조회
                  OnPropertyCondition: context.getEnvironment().getProperty(key)
                  OnBeanCondition: context.getBeanFactory()에서 타입/이름으로 빈 존재 확인
                  (커스텀 Condition도 이 시점에 완전히 동등하게 호출됨)
              → ConditionOutcome(matched, message) 반환
          → SpringBootCondition이 이 outcome을 ConditionEvaluationReport에 기록
      → 하나라도 noMatch면 이 후보(자동 설정 클래스 또는 @Bean 메서드)는 건너뜀
```

18주차의 `DeferredImportSelector` 흐름과 이 조건 평가가 정확히 어느 지점에서 맞물리는지를 그린 다이어그램: [`diagrams/condition-evaluation-flow.md`](diagrams/condition-evaluation-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.boot.autoconfigure.condition.SpringBootCondition#matches
org.springframework.boot.autoconfigure.condition.OnClassCondition#getMatchOutcome
org.springframework.boot.autoconfigure.condition.OnPropertyCondition#getMatchOutcome
org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport#recordConditionEvaluation
```

## 8. 런타임 관찰

[`ConditionalAnnotationsTest`](../../experiments/auto-configuration-lab/src/test/java/lab/experiments/autoconfig/ConditionalAnnotationsTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| `@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")`, Jackson은 `testImplementation`으로만 존재 | 테스트 실행 시점 클래스패스에는 있으므로 매치 - `JacksonProbe` 빈 등록됨 |
| `@ConditionalOnProperty(..., havingValue = "true")`에 `greeting.enabled=false` | `GreetingService` 빈이 아예 등록 안 됨(`NoSuchBeanDefinitionException`) |
| 같은 조건에 프로퍼티 자체를 안 줌(`matchIfMissing = true`) | 기본적으로 켜진 상태로 등록됨 |
| 커스텀 `Condition`(`lab.slow-mode` 프로퍼티), 프로퍼티 없음 | 등록 안 됨 |
| 같은 커스텀 `Condition`, `lab.slow-mode=true` | 등록됨 |
| `greeting.enabled=false`인 컨텍스트의 `ConditionEvaluationReport` 조회 | `GreetingAutoConfiguration`/`SlowModeAutoConfiguration` 둘 다 `isFullMatch()=false`, 이유 메시지에 `greeting.enabled`가 실제로 포함됨 |

**직접 겪은 것**: 처음에 `ConditionAndOutcomes#toString()`이 이유 메시지를 그대로 보여줄 거라 기대하고 `.toString()`으로 바로 검증했다가 실패했다 - 이 타입은 `toString()`을 오버라이드하지 않아서 기본 객체 표현(`@16e7b402`)만 나왔다. `Iterable<ConditionAndOutcome>`이라는 것을 확인하고 순회하며 `getOutcome().getMessage()`를 모아야 실제 이유 문자열을 얻을 수 있었다.

## 9. 공식 테스트 분석

이번 주도 `spring-boot-autoconfigure`의 공식 테스트 코드는 직접 열람하지 못했다(17·18주차와 같은 사정) - 정직하게 밝혀 둔다. 대신 다음을 실제 릴리스 소스로 확인했다.

- `OnClassCondition`이 `FilteringSpringBootCondition`을 상속하고, 내부적으로 `ClassNameFilter.PRESENT`/`MISSING`(클래스를 초기화 없이 존재 여부만 확인하는 유틸리티)을 쓴다는 것을 소스에서 직접 확인했다 - 7주차에서 확인한 ASM 기반 컴포넌트 스캔("클래스를 로딩하지 않고 바이트코드만 읽는다")과 정확히 같은 동기(불필요한 클래스 로딩/초기화의 부작용 회피)에서 나온 설계라는 것을 다시 확인한 셈이다.
- `SpringBootCondition`이 `Condition`(Spring Framework 코어 인터페이스)을 구현하면서 `getMatchOutcome()`이라는 새 추상 메서드로 계약을 한 단계 더 구체화한다는 것을 확인했다 - 이 덕분에 우리 커스텀 `OnSlowModeCondition`도 상속만으로 표준 조건과 동일한 리포트 통합을 얻는다.

## 10. 축소 구현 (이번 주도 생략)

18주차와 같은 이유로 mini 구현은 만들지 않았다. 20주차에서 만들 `mini-observability-spring-boot-starter`가 `@ConditionalOnMissingBean`류의 조건부 빈 등록을 실전에서 한 번 더 다루게 된다.

## 11. Spring 설계 의도

- **왜 모든 `@ConditionalOnXxx`가 결국 하나의 `Condition` 인터페이스로 수렴하는가**: 조건의 종류(클래스 존재, 빈 존재, 프로퍼티 값, 그 밖의 무엇이든)는 앞으로도 계속 늘어날 수 있다. 만약 Spring이 "클래스 조건", "빈 조건", "프로퍼티 조건"마다 서로 다른 처리 엔진을 만들었다면, 새로운 종류의 조건이 필요할 때마다 프레임워크 코어를 건드려야 했을 것이다. `Condition`이라는 단일하고 좁은 인터페이스(메서드 하나)로 수렴시켜 두면, 새로운 조건은 그저 이 인터페이스의 새 구현체일 뿐이고, `ConfigurationClassParser`의 평가 로직은 전혀 바뀔 필요가 없다 - 11주차에서 본 "확장점은 좁고 합성 가능하게 쪼갠다"는 원칙이 여기서는 "하나의 인터페이스로 무한히 확장 가능하게 만든다"는 형태로 나타난다.
- **왜 `@ConditionalOnClass`는 클래스를 초기화하지 않고 존재 여부만 확인하는가**: 만약 클래스를 실제로 로딩하고 초기화까지 해 버리면, 그 클래스의 `static` 블록이 부작용(다른 클래스 로딩, 리소스 초기화 등)을 일으킬 수 있다 - 단지 "이 클래스가 있는지 없는지"만 알고 싶었을 뿐인데, 그 확인 행위 자체가 애플리케이션 상태를 바꿔 버리는 것은 위험하다. `ClassUtils.isPresent()`류의 유틸리티가 "로딩은 하되 초기화는 하지 않는" `Class.forName(name, false, classLoader)` 형태의 API를 쓰는 것은 정확히 이 부작용을 피하기 위해서다.
- **왜 `matchIfMissing`을 지원하는가**: 많은 자동 설정 기능은 "명시적으로 끄지 않는 한 켜져 있는" 것이 사용자 경험상 자연스럽다(예: 우리 실험의 `greeting.enabled`). 그런데 `@ConditionalOnProperty`의 기본 동작(프로퍼티가 없으면 불일치)만 있다면, 이런 "기본적으로 켜짐" 기능마다 매번 `havingValue`를 이중 부정으로 표현하는 번거로움이 생긴다. `matchIfMissing`은 이 흔한 요구를 위한 전용 스위치다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 서로 완전히 달라 보이는 `@ConditionalOnClass`/`@ConditionalOnProperty`/`@ConditionalOnBean`이 전부 `Condition` 인터페이스 하나의 구현체일 뿐이라는 것 - 커스텀 조건을 실제로 만들어서 표준 조건과 똑같이 취급되는 것을 확인하고 나서야 이 통일성이 단순한 설명이 아니라 실제 코드 구조라는 것을 체감했다.
- 예상대로였던 것(재확인): 클래스 존재 확인이 클래스를 초기화하지 않는다는 것 - 7주차 컴포넌트 스캔에서 배운 원칙이 다른 곳에서도 일관되게 적용된다는 걸 다시 확인했다.
- 예상 밖이었던 것: `ConditionAndOutcomes`가 `toString()`을 오버라이드하지 않아서, 리포트를 "보기 좋게" 조회하려면 직접 순회해야 한다는 것 - 사소하지만 API를 오해하고 있었다는 걸 실행해서야 깨달았다.
- 18주차와의 연결: 18주차의 `DeferredImportSelector`가 "언제" 후보를 골라내는지를 정했다면, 19주차의 `Condition`은 "무엇을 기준으로" 걸러내는지를 정한다 - 이 둘이 합쳐진 것이 자동 설정의 전체 그림이다.
- 다음 주로 이어지는 질문: 지금까지 다룬 세 가지(`.imports` 파일 탐색, `before`/`after` 순서, `Condition` 기반 필터링)를 전부 실제로 조합해서 하나의 Starter를 처음부터 끝까지 만들어 보는 것이 20주차(Starter와 AutoConfiguration 직접 구현)다.
