# ConfigurationCondition / ConfigurationPhase — "언제 평가되는가"가 "무엇을 볼 수 있는가"를 결정한다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`46`](../46-generic-dependency-resolution/generic-dependency-resolution.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 지금까지 `@Conditional`은 그저 "조건이 참이면 등록, 거짓이면 스킵"하는 단일 시점 스위치로만 다뤘다. 하지만 Spring Boot의 `@ConditionalOnMissingBean`처럼 "다른 빈이 이미 등록됐는지"를 검사하는 조건은, 검사 시점 자체가 결과를 좌우한다 - `ConfigurationCondition`이 왜 `PARSE_CONFIGURATION`과 `REGISTER_BEAN`이라는 두 단계를 구분해서 노출하는지, 그리고 이 구분을 잘못 쓰면 실제로 무슨 일이 벌어지는지를 이번에 직접 재현해서 확인한다.

## 1. 이번 질문

- 클래스 레벨 `@Conditional`에 평범한 `Condition`(위상을 선언하지 않는)을 붙이면, 그 조건은 정확히 "언제" 평가되는가?
- `ConfigurationCondition`을 구현해서 `REGISTER_BEAN` 위상을 선언하면, 평범한 `Condition`과 실제로 다르게 동작하는 지점이 어디인가?
- "다른 `@Bean`이 이미 등록됐는가"를 검사하는 조건을, 아무 위상이나 선언해서 써도 항상 안전한가?
- `@ComponentScan`처럼 파싱 시점에 즉시 부수 효과를 내는 기능과, 위상을 잘못 조합하면 무슨 일이 벌어지는가?

## 2. 공식 문서 요약

- `ConfigurationCondition` 인터페이스의 Javadoc은 `ConfigurationPhase`를 두 값으로 정의한다: `PARSE_CONFIGURATION`("the @Configuration class should be skipped entirely, including the registration of `@Import` and `@Bean` definitions within it" 수준의 언급 - 클래스 자체를 파싱할지 말지 결정)과 `REGISTER_BEAN`("even though a condition may not match... a `@Configuration` class may still be parsed" - 파싱은 이미 끝났고 오직 그 안의 특정 `@Bean` 정의를 등록할지만 결정).
- Javadoc은 `REGISTER_BEAN`이 "다른 빈 정의의 존재 여부를 기준으로 판단하는 조건"에 적합하다고 명시한다 - `PARSE_CONFIGURATION` 시점에는 다른 `@Configuration` 클래스들이 아직 파싱조차 안 됐을 수 있어서 그 존재를 신뢰할 수 없기 때문이라는 취지다.
- 문서는 이 두 위상의 차이를 개념적으로만 설명할 뿐, "평범한 `Condition`(위상 미선언)이 실제로는 정확히 어느 위상에서 평가되는가", "그 평가가 몇 번 일어나는가" 같은 실행 세부사항은 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 평범한 `Condition`(위상 미선언)을 클래스 레벨에 붙이면 `PARSE_CONFIGURATION` 시점에 **딱 한 번만** 평가되고, 그 결과가 그대로 최종 결과가 될 거라 예상했다 — **틀렸다.** 실제로는 같은 조건 인스턴스가 **파싱 단계**(`ConfigurationClassParser`)와, 각 설정 클래스의 빈 정의를 로드하기 직전에 다시 도는 **`REGISTER_BEAN` 안전망**(`ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator`) 양쪽에서 **최소 두 번** 평가된다 - 위상을 선언하지 않았다는 것이 "한 번만 평가된다"가 아니라 "어느 위상 검사에도 걸린다"는 뜻이었다.
- 그래서 "provider 빈이 나중에 등록되면, 평범한 조건은 그걸 못 보고 자기 빈을 중복 등록해 버릴 것"이라 예상했다 — **틀렸다.** `@Bean` 메서드만 있는 단순한 설정 클래스라면, 이 `REGISTER_BEAN` 안전망 재평가 덕분에 평범한 조건도 `ConfigurationCondition(REGISTER_BEAN)`과 **똑같은 최종 결과**로 수렴한다 - 등록 순서만 "provider가 먼저"라면, 두 조건 방식 모두 자기 자신의 등록을 정확히 건너뛴다.
- 위상 선언의 차이가 아예 관찰 불가능한 건 아닐 거라 예상했고, 실제로 `@ComponentScan`을 붙여 보니 확인됐다 — **부분적으로 맞았다.** `@ComponentScan`은 파싱 단계에서 **즉시** 스캔을 실행해 버리므로(빈 정의 등록처럼 나중으로 미뤄지지 않는다), `REGISTER_BEAN` 위상 조건과 `@ComponentScan`을 같은 클래스에 함께 쓰면 Spring이 **`ApplicationContextException`을 즉시 던져서 그 조합 자체를 거부**한다는 것까지는 예상하지 못했다 - "잘못 동작한다"가 아니라 "아예 시작을 막는다"는 훨씬 방어적인 설계였다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/configuration-condition-phase-lab`](../../experiments/configuration-condition-phase-lab)

```java
// 위상 미선언 - requiredPhase가 없어 "어느 위상 검사든" 평가 대상에 포함된다
public class PlainOnMissingBeanCondition implements Condition {
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !context.getRegistry().containsBeanDefinition("shared");
    }
}

// REGISTER_BEAN 위상만 명시 - PARSE_CONFIGURATION 검사에서는 아예 평가되지 않는다
public class PhasedOnMissingBeanCondition implements ConfigurationCondition {
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !context.getRegistry().containsBeanDefinition("shared");
    }
    public ConfigurationPhase getConfigurationPhase() { return ConfigurationPhase.REGISTER_BEAN; }
}
```

```java
@Configuration
public class SharedBeanProviderConfig {
    @Bean
    public SharedValue shared() { return new SharedValue("from-provider"); }
}

@Configuration
@Conditional(PlainOnMissingBeanCondition.class)
public class PlainConditionalFallbackConfig {
    @Bean
    public SharedValue shared() { return new SharedValue("from-plain-fallback"); }
}
```

```java
// provider가 먼저 등록되면: 평범한 조건도 REGISTER_BEAN 안전망 덕분에 정확히 자기 자신을 건너뜀
new AnnotationConfigApplicationContext(SharedBeanProviderConfig.class, PlainConditionalFallbackConfig.class)
    .getBean(SharedValue.class).source();  // "from-provider" (조건이 스스로 건너뜀)

// 등록 순서를 뒤집으면: fallback이 먼저 등록되어 자기 빈을 실제로 등록해 버리고,
// provider가 나중에 같은 이름을 조건 없이 덮어씀(doc45의 override 규칙)
new AnnotationConfigApplicationContext(PlainConditionalFallbackConfig.class, SharedBeanProviderConfig.class)
    .getBean(SharedValue.class).source();  // "from-provider" (결과는 같지만 이유는 완전히 다름)
```

```java
@Configuration
@Conditional(PlainOnMissingBeanCondition.class)
@ComponentScan(basePackageClasses = ScannedComponent.class)
public class PlainConditionalScanConfig { }

@Configuration
@Conditional(PhasedOnMissingBeanCondition.class)
@ComponentScan(basePackageClasses = ScannedComponent.class)
public class PhasedConditionalScanConfig { }
// → PhasedConditionalScanConfig를 register()+refresh()하면 즉시:
//   ApplicationContextException: "Component scan for configuration class [...]
//   could not be used with conditions in REGISTER_BEAN phase: [...]"
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ConfigurationCondition` | `Condition`을 확장해 `getConfigurationPhase()`를 추가로 요구하는 인터페이스 - `PARSE_CONFIGURATION` \| `REGISTER_BEAN` |
| `ConditionEvaluator#shouldSkip(metadata, phase)` | 조건 목록을 순회하며, 조건이 `ConfigurationCondition`이면 `requiredPhase == phase`일 때만, 평범한 `Condition`(`requiredPhase == null`)이면 **어느 phase로 호출되든 항상** `matches()`를 실제로 호출한다 |
| `ConfigurationClassParser#processConfigurationClass` | 클래스 처리 맨 앞에서 `shouldSkip(metadata, PARSE_CONFIGURATION)`을 호출 - 여기서 건너뛰면 `@Import`/`@ComponentScan`/`@PropertySource` 등 그 클래스의 모든 파싱 자체가 시작조차 안 됨 |
| `ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator` | `reader.loadBeanDefinitions()`가 각 설정 클래스의 `@Bean` 메서드를 실제로 등록하기 **직전에** `shouldSkip(metadata, REGISTER_BEAN)`을 다시 호출하는 안전망 - `@Import`로 끌어들여진 클래스라면 자신을 임포트한 클래스가 건너뛰어졌는지까지 재귀적으로 함께 확인한다 |
| `ComponentScanAnnotationParser` | `@ComponentScan`을 만나면 `@Bean`처럼 모델링만 해 두는 게 아니라 **그 자리에서 즉시** `scanner.doScan()`을 실행해 빈 정의를 등록한다 - 이 즉시성이 `REGISTER_BEAN` 위상과 원천적으로 충돌하는 이유 |

## 6. 호출 흐름

```text
[ConfigurationClassPostProcessor#processConfigBeanDefinitions]
  1) parser.parse(candidates)                          ← "파싱" 배치 - 후보 전체를 순서대로 처리
       각 클래스마다 processConfigurationClass():
         shouldSkip(metadata, PARSE_CONFIGURATION) 체크
           - ConfigurationCondition(REGISTER_BEAN 선언): requiredPhase != PARSE_CONFIGURATION → 평가 자체를 건너뜀 → 항상 통과
           - 평범한 Condition(위상 미선언): requiredPhase == null → 평가됨 → 이 시점엔 형제 클래스의 @Bean이 아직
             하나도 등록 안 됐으므로(2번 단계가 아직 시작 전) "없다"고 판단해 통과하기 쉬움
         통과하면 @ComponentScan을 이 자리에서 즉시 실행(스캔된 빈은 바로 registry에 등록됨)
         통과하면 @Import 처리 → 임포트된 클래스도 재귀적으로 파싱(같은 체크 반복)
  2) reader.loadBeanDefinitions(configClasses)          ← "등록" 배치 - 파싱된 클래스들의 순서대로 @Bean 등록
       각 클래스마다 loadBeanDefinitionsForConfigurationClass():
         TrackedConditionEvaluator.shouldSkip(configClass) 체크
           - shouldSkip(metadata, REGISTER_BEAN) 재호출
               - ConfigurationCondition(REGISTER_BEAN 선언): 이번엔 requiredPhase == phase → 실제로 평가됨
               - 평범한 Condition: 위상 무관하게 다시 평가됨 → 이번엔 자신보다 먼저 처리된 형제 클래스의
                 @Bean이 이미 등록돼 있을 수 있음(등록 순서상 앞선 클래스라면)
           - 건너뛰기로 판정되면: 이 클래스가 @Import로 끌어들인 클래스들도 "임포트한 쪽이 건너뛰어졌다"는
             이유로 함께 건너뛰어짐(재귀 전파) - 단, 이미 1)번에서 즉시 실행된 @ComponentScan의
             부수 효과(등록된 빈)는 되돌려지지 않음
```

`@Bean`만 있는 단순한 설정 클래스에서는, 등록 순서(provider가 먼저냐 나중이냐)에 따라 "조건이 스스로 건너뜀"과 "조건은 통과했지만 나중에 덮어써짐"이라는 서로 다른 두 경로가 같은 최종 승자로 수렴한다. `@ComponentScan`이 끼면 이 수렴이 깨지고 위상 선언의 차이가 그대로 드러난다: [`diagrams/configuration-condition-phase-flow.md`](diagrams/configuration-condition-phase-flow.md)

## 7. 브레이크포인트

이번 주제도 25~46번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 핵심 질문이 "조건이 몇 번, 어떤 registry 상태에서 평가되는가"였고, `PlainOnMissingBeanCondition.matches()` 안에 임시로 `System.out.println`을 넣어 `context.getRegistry().getBeanDefinitionNames()`를 함께 찍어 보는 것만으로 정확히 세 번(파싱 시작 직전, 형제 클래스 파싱 후, `REGISTER_BEAN` 안전망) 호출되는 것을 직접 확인할 수 있었다. 실제 소스에서 이 흐름을 뒷받침하는 지점은 다음과 같다(`spring-framework-src`, v6.2.19 로컬 체크아웃):

```text
org.springframework.context.annotation.ConditionEvaluator#shouldSkip
org.springframework.context.annotation.ConfigurationClassParser#processConfigurationClass
org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$TrackedConditionEvaluator#shouldSkip
org.springframework.context.annotation.ConfigurationClassParser#doProcessConfigurationClass   (collectRegisterBeanConditions 예외 발생 지점)
```

## 8. 런타임 관찰

[`ConfigurationConditionPhaseTest`](../../experiments/configuration-condition-phase-lab/src/test/java/lab/experiments/configphase/ConfigurationConditionPhaseTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| provider 먼저 등록 + 평범한 조건의 fallback | `"from-provider"` - fallback이 `REGISTER_BEAN` 안전망에서 스스로 건너뜀 |
| fallback 먼저 등록 + provider 나중 (평범한 조건, 순서 반전) | `"from-provider"` - 하지만 fallback의 빈이 실제로 등록됐다가 provider에게 조용히 덮어써진 것(doc45 override 규칙) |
| provider 먼저 등록 + `ConfigurationCondition(REGISTER_BEAN)` 선언 fallback | `"from-provider"` - `@Bean`만 있는 이 시나리오에서는 평범한 조건과 결과가 수렴함 |
| `@ComponentScan` + 평범한 조건, "shared" 없음 | 스캔 실행됨 - `ScannedComponent` 등록됨 |
| `@ComponentScan` + 평범한 조건, "shared"를 파싱 전에 직접 등록해 둠 | `PARSE_CONFIGURATION` 체크에서 즉시 건너뜀 → 스캔 자체가 실행 안 됨 - `ScannedComponent` 미등록 |
| `@ComponentScan` + `ConfigurationCondition(REGISTER_BEAN)` 선언 | `ApplicationContextException`("could not be used with conditions in REGISTER_BEAN phase") - "shared" 존재 여부와 무관하게 조합 자체가 거부됨 |

**직접 겪은 것**: 처음 세운 가설은 "평범한 `Condition`은 항상 `PARSE_CONFIGURATION` 시점 한 번만 평가되므로, provider가 나중에 등록되면 그 존재를 못 보고 무조건 중복 등록해 버릴 것"이었다. 실제로 스파이크 테스트를 돌려 보니 `PLAIN`과 `PHASED` 시나리오가 **둘 다** `"from-provider"`를 반환해서 가설이 완전히 빗나갔다는 걸 바로 알 수 있었다. 조건 안에 registry 상태를 함께 찍는 진단 코드를 추가하고 나서야, 같은 조건이 **세 번** 호출되고(마지막 호출 시점엔 이미 "shared"가 등록돼 있었다) 있다는 걸 발견했다 - `requiredPhase == null`이 "phase 검사를 안 받는다"가 아니라 정반대로 "모든 phase 검사에 다 걸린다"는 뜻이었다는 걸, 소스를 다시 읽고서야 정확히 이해했다. 이 발견 덕분에 애초에 계획했던(그리고 반증된) "provider 등록 순서 비교" 실험만으로는 `PLAIN`/`PHASED`의 차이가 전혀 드러나지 않는다는 것도 알게 됐고, `@Import`로 부수 효과 빈을 끌어들이는 실험을 시도했으나 이마저도 `TrackedConditionEvaluator`의 `importedBy` 재귀 전파 때문에 차이가 가려진다는 걸 다시 확인한 뒤에야(관련 소스 코드 인용은 커밋 이력 참고), `@ComponentScan`이라는 "파싱 시점에 즉시 실행되는" 진짜 예외 케이스로 재설계해서야 두 위상의 실제 차이를 관찰할 수 있었다.

## 9. 공식 테스트 분석

`@ComponentScan` + `REGISTER_BEAN` 위상 조합을 거부하는 로직은 Spring 자신의 소스 안에 있는 명시적 가드다(`ConfigurationClassParser#doProcessConfigurationClass`의 `collectRegisterBeanConditions` 호출과 뒤이은 `ApplicationContextException`) - 이 저장소가 반복해서 확인해 온 "공식 테스트가 곧 설계 의도의 증거"라는 원칙을, 이번엔 별도 테스트 코드를 찾아 인용하는 대신 **런타임 예외 메시지 자체**가 대신했다: `"Component scan for configuration class [...] could not be used with conditions in REGISTER_BEAN phase: [...]"`라는 문구가, Spring 팀이 이 조합의 위험성을 인지하고 있었고 우연이 아니라 의도적으로 막아 뒀다는 것을 그 자체로 증명한다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `ConfigurationClassParser`/`ConfigurationClassBeanDefinitionReader`의 "파싱 배치 → 등록 배치"라는 2단계 파이프라인과 `TrackedConditionEvaluator`의 재귀적 `importedBy` 전파는, 이번 주의 핵심 질문(위상 선언이 평가 시점과 횟수를 어떻게 바꾸는가)에 비해 축소 구현으로 재현하기엔 지나치게 큰 파이프라인이었다 - 7주차의 `mini-java-config`가 이미 다룬 "설정 클래스 파싱 → `@Bean` 등록"이라는 기본 골격 위에, 이번 주에 확인한 "위상별로 조건이 평가되는 시점이 다르다"는 규칙을 개념적으로 얹어 이해하는 것으로 충분했다.

## 11. Spring 설계 의도

- **왜 `PARSE_CONFIGURATION`과 `REGISTER_BEAN`을 굳이 분리했는가**: 파싱(클래스 구조 분석, `@Import`/`@ComponentScan` 처리)과 빈 정의 등록은 서로 다른 신뢰 수준의 정보에 의존한다. 파싱 시점엔 "다른 설정 클래스가 어떤 빈을 제공할지"가 아직 확정되지 않았을 수 있으므로(형제 클래스가 아직 파싱 전일 수 있음), 그 시점에 다른 빈의 존재를 조건으로 삼는 것은 원천적으로 신뢰할 수 없는 정보에 기대는 것이다. `REGISTER_BEAN`은 "적어도 파싱은 전부 끝났다"는 더 강한 보장 위에서 판단하라는 명시적 계약이다.
- **왜 평범한 `Condition`도 결과적으로 `REGISTER_BEAN` 안전망의 혜택을 받는가**: `requiredPhase == null`을 "위상을 모르니 아무 때나 평가해도 되는 걸로 취급"하는 관대한 설계 덕분에, 위상을 명시하지 않은 조건이라도 최소한 한 번은 "파싱이 끝난 뒤"의 상태로도 재평가받을 기회를 얻는다. 이건 실수로 위상을 선언하지 않은 조건이 항상 최악의(가장 이른) 시점의 정보만으로 판단하게 되는 것을 막아 주는, 하위 호환성과 안전성을 동시에 노리는 설계로 읽힌다.
- **왜 `@ComponentScan` + `REGISTER_BEAN` 조합을 조용히 무시하지 않고 예외로 막았는가**: `@ComponentScan`은 `@Bean`과 달리 "모델링 후 나중에 등록"이 불가능한, 파싱 시점에 즉시 실행되는 부수 효과다. 만약 이 조합을 허용했다면, 개발자는 `REGISTER_BEAN`이라는 이름만 보고 "다른 빈이 등록된 뒤에 스캔 여부가 결정될 것"이라 오해하겠지만 실제로는 스캔이 조건과 무관하게 이미 끝나 있는, 이름과 동작이 완전히 어긋나는 상황이 조용히 발생했을 것이다. 즉시 예외를 던지는 것은, 이 저장소가 44번(`AopContext`/`exposeProxy`) 문서에서도 확인했던 "잘못된 사용을 조용히 허용하기보다 명확하게 실패시킨다"는 Spring의 반복되는 방어적 설계 원칙과 같은 맥락이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 애초에 세운 가설(평범한 `Condition`은 한 번만 평가되어 provider의 존재를 놓친다) 자체가 틀렸다는 것 - 실행해 보기 전까지는 `requiredPhase == null`이 "위상 검사 열외"를 뜻한다고 무의식적으로 생각했는데, 실제로는 정반대로 "모든 위상 검사에 포함"을 뜻했다. 이 하나의 오해가 처음 설계한 실험 전체(단순 `@Bean` 등록 순서 비교)를 무의미하게 만들었고, 왜 무의미해졌는지를 직접 진단 코드로 추적하고 나서야 그 이유를 정확히 말할 수 있게 됐다.
- 예상 밖이었던 것: `@Import`로 부수 효과 빈을 끌어들여 차이를 드러내려던 두 번째 시도도, `TrackedConditionEvaluator`가 `importedBy` 체인을 재귀적으로 따라가며 "임포트한 클래스가 건너뛰어졌으면 임포트된 클래스도 건너뛴다"는 것까지 챙기고 있어서 또 한 번 막혔다 - Spring의 조건 평가가 생각보다 훨씬 촘촘하게, 우회 가능한 허점을 최소화하도록 설계돼 있다는 걸 두 번의 실패한 시도를 통해 체감했다.
- 예상대로였던 것(부분적으로): 위상 선언이 실제로 다른 결과를 만드는 지점이 존재할 거라는 직감은 맞았지만, 그 지점이 "일반적인 `@Bean` 등록 순서"가 아니라 "`@ComponentScan`처럼 파싱 시점에 즉시 실행되는 부수 효과와의 조합"이라는, 훨씬 좁고 구체적인 경우였다는 것까지는 소스를 읽기 전엔 짐작하지 못했다.
- 새로 배운 것: `ConfigurationCondition`의 두 위상 값이 "언제 평가할지 고르는 옵션"이 아니라, 어떤 부수 효과(즉시 실행 vs 지연 등록)와 결합 가능한지를 규정하는 **호환성 계약**에 더 가깝다는 것 - Spring Boot의 `@ConditionalOnMissingBean`이 `REGISTER_BEAN` 위상으로 고정돼 있고 `@ComponentScan`과 함께 쓸 수 없다는 사실이, 이번 실험을 통해 막연한 "그렇다더라"가 아니라 실행으로 확인한 구체적 제약이 됐다.
