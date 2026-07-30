# Starter와 AutoConfiguration 직접 구현 — 18·19주차를 하나로 조립하기

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 20주차(선택 과정: Spring Boot 내부, 마지막 주), [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 32(Custom AutoConfiguration)에 대응하는 분석 문서다. 카탈로그가 제시한 `request-observation` 모듈 구조를 그대로 따르되, 로드맵의 이름(`mini-observability-spring-boot-starter`)에 맞춰 명명했다. 이번 주는 새 메커니즘을 배우는 대신, 18주차(자동 설정 탐색·순서)와 19주차(조건부 활성화)에서 확인한 것들을 실제로 동작하는 3모듈 스타터 하나로 조립하는 데 집중했다.

## 1. 이번 질문

- 실제 Spring Boot 스타터가 `core`/`autoconfigure`/`starter`로 나뉘는 이유는 단순한 관례인가, 아니면 강제되는 구조인가?
- `@ConditionalOnMissingBean`이 걸린 빈을 다른 빈이 의존해야 할 때, 그 조건이 불일치하면 무슨 일이 일어나는가 - 이걸 안전하게 배선하려면 무엇이 필요한가?
- 카탈로그가 요구하는 5가지 테스트(기본 등록/비활성화/사용자 정의 우선/비-웹 환경/프로퍼티 바인딩)를 전부 통과하는 스타터를 실제로 완성할 수 있는가?

## 2. 공식 문서 요약

- Spring Boot 레퍼런스("Creating Your Own Auto-configuration", "Custom Starters")는 스타터를 `자동 설정(autoconfigure)`과 `스타터(starter)` 두 모듈로 나누라고 권장한다 - 자동 설정 로직과 실제 의존성 선언을 분리해서, 자동 설정만 재사용하고 싶은 사용자(예: 다른 스타터를 이미 쓰고 있는 경우)가 의존성 중복 없이 가져다 쓸 수 있게 하기 위해서다.
- `core`(순수 로직) 모듈을 별도로 두는 것은 Spring Boot 자체의 공식 권장 사항이라기보다, 로직을 Boot/자동 설정에 대한 의존 없이도 테스트하고 재사용할 수 있게 하려는 일반적인 관례다 - 카탈로그가 3모듈 구조를 명시한 것도 이 관례를 따른 것이다.
- `ApplicationContextRunner`/`WebApplicationContextRunner`가 자동 설정 테스트의 표준 도구로 안내되고, `AutoConfigurations.of(...)`로 특정 자동 설정만 골라 테스트 컨텍스트에 올릴 수 있다고 설명한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `autoconfigure` 모듈이 `core`를 의존하기만 하면 `starter`를 쓰는 최종 애플리케이션에서도 자연스럽게 `core`의 타입(`RequestObservationProperties` 등)을 쓸 수 있을 거라 예상했다 - **반은 맞고 반은 틀렸다.** Gradle에서 `implementation`으로 의존하면 그 의존성이 전이적으로 노출되지 않는다 - `api`로 선언해야 한다는 것을, 이 저장소의 루트 빌드가 기본적으로 `java` 플러그인만 적용해서(그래서 `api` 설정 자체가 없어서) 빌드 실패를 겪고서야 확인했다.
- `@ConditionalOnMissingBean`이 걸린 `RequestObservationInterceptor`를 다른 `@Bean`(인터셉터를 실제로 등록하는 `WebMvcConfigurer`)이 생성자 인자로 그냥 받으면 될 거라 예상했다 - 조건이 불일치해서 그 빈이 아예 없을 때 생성자 주입이 깨질 위험을 미리 알아채고, `ObjectProvider<RequestObservationInterceptor>`로 "있으면 등록, 없으면 조용히 넘어간다"는 방어적 설계로 처음부터 바꿨다(실제로 겪은 버그는 아니고, 19주차에서 배운 조건부 등록의 함의를 미리 반영한 것이다).
- `@EnableAutoConfiguration`을 실제 웹 통합 테스트(`AnnotationConfigWebApplicationContext` + `MockServletContext`, 임베디드 서버 없음)에 쓰면 Spring Boot의 표준 웹 자동 설정(`DispatcherServletAutoConfiguration` 등)까지 전부 딸려 오면서 무언가 충돌하거나 실패할 거라 예상했다 - **틀렸다.** 아무 문제 없이 그대로 동작했다.

## 4. 최소 재현 코드

**`core`** — 순수 로직, Boot 자동 설정에 대한 의존이 전혀 없다.
```java
@ConfigurationProperties(prefix = "request-observation")
public class RequestObservationProperties {
    private boolean enabled = true;
    private Duration slowThreshold = Duration.ofMillis(500);
    private List<String> excludePaths = List.of();
    // ...
}
```

**`autoconfigure`** — [`spring-extensions/mini-observability-starter`](../../spring-extensions/mini-observability-starter)
```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(RequestObservationProperties.class)
public class RequestObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "request-observation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestObservationInterceptor requestObservationInterceptor(
            RequestObservationProperties properties, ObservationLog observationLog) {
        return new RequestObservationInterceptor(properties, observationLog);
    }

    @Bean
    public WebMvcConfigurer requestObservationWebMvcConfigurer(
            ObjectProvider<RequestObservationInterceptor> interceptorProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                interceptorProvider.ifAvailable(registry::addInterceptor);   // 없어도 안전
            }
        };
    }
}
```

**`starter`** — 코드 없음, 의존성 선언만.
```kotlin
dependencies {
    api(project(":spring-extensions:mini-observability-starter:autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-web:3.5.0")
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `RequestObservationProperties` | `@ConfigurationProperties` - `Duration`/`List<String>` 타입 바인딩까지 확인 |
| `RequestObservationInterceptor` | 실제 관찰 로직(경로 제외, 소요시간 측정, 느린 요청 판정) - `core`에 있어 자동 설정과 독립적으로 단위 테스트 가능 |
| `RequestObservationAutoConfiguration` | 18·19주차의 모든 메커니즘(`.imports` 탐색, `@ConditionalOnWebApplication`/`@ConditionalOnClass`/`@ConditionalOnMissingBean`/`@ConditionalOnProperty`)이 한곳에 모인 자리 |
| `ObjectProvider<T>` | 조건부로 존재할 수도, 없을 수도 있는 빈을 안전하게 참조하는 표준 수단 - 생성자 주입 필수 의존성 대신 씀 |
| `WebApplicationContextRunner`/`ApplicationContextRunner` | 실제 서버 없이 자동 설정의 조건부 동작을 빠르게 검증하는 표준 테스트 도구 |

## 6. 호출 흐름

```text
사용자 애플리케이션이 mini-observability-starter:starter만 의존
  → starter가 끌고 온 것: autoconfigure(api) + spring-boot-starter-web(api)
  → autoconfigure가 끌고 온 것: core(api) + spring-boot-autoconfigure
  → SpringApplication.run() (17주차)
      → @EnableAutoConfiguration(사용자 앱의 @SpringBootApplication 안에 포함) 처리
          → DeferredImportSelectorHandler.process() (18주차)
              → ImportCandidates.load(...)로 여러 classloader.getResources() 결과를 합침
                  (spring-boot-autoconfigure 자체의 .imports + 우리 autoconfigure 모듈의 .imports)
              → AutoConfigurationSorter로 정렬
              → 각 후보에 Condition 평가(19주차) - RequestObservationAutoConfiguration도 이 중 하나
                  @ConditionalOnWebApplication(SERVLET) - 통과(서블릿 웹 앱이므로)
                  @ConditionalOnClass(HandlerInterceptor.class) - 통과(spring-webmvc가 있으므로)
                  @ConditionalOnProperty(enabled) - 프로퍼티 값에 따라 갈림
                  @ConditionalOnMissingBean - 사용자가 이미 등록했는지에 따라 갈림
      → 통과한 빈들이 실제로 등록되고, WebMvcConfigurer#addInterceptors가 실제 요청 처리
        파이프라인(15주차의 HandlerExecutionChain)에 인터셉터를 끼워 넣음
      → 실제 HTTP 요청 → DispatcherServlet → 인터셉터 preHandle/afterCompletion → ObservationLog
```

3모듈이 의존하는 방향과, `@ConditionalOnMissingBean`이 불일치할 때 `ObjectProvider`가 어떻게 안전망 역할을 하는지를 함께 그린 다이어그램: [`diagrams/starter-module-flow.md`](diagrams/starter-module-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)로 검증했고, 소스 확인은 18·19주차에서 이미 마쳤으므로 새로 추가하지 않았다.

```text
lab.ext.observability.autoconfigure.RequestObservationAutoConfiguration#requestObservationInterceptor
lab.ext.observability.core.RequestObservationInterceptor#preHandle
lab.ext.observability.core.RequestObservationInterceptor#afterCompletion
```

## 8. 런타임 관찰

[`RequestObservationInterceptorTest`](../../spring-extensions/mini-observability-starter/core/src/test/java/lab/ext/observability/core/RequestObservationInterceptorTest.java) (4개, `core` - 순수 단위 테스트):

| 실험 | 결과 |
| --- | --- |
| 임계값보다 빠른 요청 | `slow=false`로 기록 |
| 임계값을 `Duration.ZERO`로 설정 | 항상 `slow=true`(실제 `Thread.sleep()` 없이도 느린 요청 분기를 재현) |
| `/actuator/**` 패턴에 매칭되는 경로 | 아예 기록 안 됨 |
| 매칭 안 되는 경로 | 정상 기록 |

[`RequestObservationAutoConfigurationTest`](../../spring-extensions/mini-observability-starter/autoconfigure/src/test/java/lab/ext/observability/autoconfigure/RequestObservationAutoConfigurationTest.java) (5개, `autoconfigure` - 카탈로그가 요구한 5가지 그대로):

| 실험 | 결과 |
| --- | --- |
| 기본 설정(`WebApplicationContextRunner`) | 인터셉터/`ObservationLog`/`WebMvcConfigurer` 전부 등록 |
| `request-observation.enabled=false` | 인터셉터 없음, `WebMvcConfigurer`는 여전히 등록되지만 `ObjectProvider.ifAvailable`이 아무것도 안 함 |
| 사용자가 직접 `RequestObservationInterceptor` 빈 등록 | 자동 설정이 물러나고 사용자 인스턴스가 그대로 유지(`isSameAs`로 확인) |
| 평범한(비-웹) `ApplicationContextRunner` | `@ConditionalOnWebApplication(SERVLET)` 불일치로 자동 설정 전체가 아예 평가 안 됨 |
| `slow-threshold=200ms`, `exclude-paths[0]`/`[1]` | `Duration`/`List<String>` 둘 다 정확히 바인딩됨 |

[`RequestObservationEndToEndTest`](../../spring-extensions/mini-observability-starter/autoconfigure/src/test/java/lab/ext/observability/autoconfigure/RequestObservationEndToEndTest.java) (1개): `@EnableAutoConfiguration`만 붙인 설정 클래스(명시적 `@Import` 없음) + `MockMvc`로 실제 `GET /api/hello` 요청을 보내고, `ObservationLog`에 그 경로가 실제로 기록되는지 확인 - **자동 발견부터 실제 인터셉터 동작까지 한 번에** 검증됐다.

**직접 겪은 것**: `autoconfigure`/`starter` 모듈의 `build.gradle.kts`에서 `api(project(...))`을 쓰자마자 "Unresolved reference: api" 빌드 오류가 났다 - 이 저장소의 루트 빌드가 모든 서브모듈에 `java` 플러그인만 적용하고 있어서(`api`/`implementation` 구분이 없는 기본 `java` 플러그인), `java-library`가 제공하는 `api` 설정 자체가 없었다. 두 모듈에 개별적으로 `` `java-library` `` 플러그인을 추가해서 해결했다.

## 9. 공식 테스트 분석

18·19주차와 같은 이유로 공식 테스트 코드는 직접 열람하지 못했다 - 대신 이번 주는 우리가 만든 5가지 테스트 자체가 카탈로그(project 32)의 "반드시 작성할 테스트" 요구사항을 글자 그대로 재현한 것이라, 그 대응을 여기 명시적으로 적어 둔다.

| 카탈로그 요구사항 | 대응 테스트 |
| --- | --- |
| 기본 설정에서 빈 생성 | `defaultConfigurationRegistersTheInterceptorAndTheObservationLog` |
| `enabled=false`이면 생성하지 않음 | `disablingThePropertyPreventsTheInterceptorFromBeingRegistered` |
| 사용자 정의 빈이 있으면 자동 설정이 물러남 | `userDefinedInterceptorMakesTheAutoConfigurationBackOff` |
| WebMVC가 없으면 생성하지 않음 | `regularNonWebApplicationContextNeverActivatesThisAutoConfiguration` |
| 프로퍼티 바인딩 검증 | `propertiesBindDurationAndListSyntaxCorrectly` |

## 10. 축소 구현 (이번 주가 축소 구현이다)

이번 주 자체가 16주 핵심 과정과 17~19주차에서 배운 것들의 "축소 구현판 실전 적용"이다. 별도의 mini 구현을 새로 만들지 않았다 - `mini-observability-starter` 프로젝트 전체가 그 역할을 한다.

**구현한 것**: 요청 로깅/실행시간 측정(`RequestObservationInterceptor`), 커스텀 설정 프로퍼티(`RequestObservationProperties`, `Duration`/`List` 바인딩), 조건부 빈 등록(`@ConditionalOnMissingBean`/`@ConditionalOnProperty`/`@ConditionalOnWebApplication`/`@ConditionalOnClass`), 자동 설정(`.imports` 파일), Starter 모듈 분리(core/autoconfigure/starter) - 로드맵 20주차가 요구한 항목 전부.

**생략한 것**: 실제 Micrometer 연동(`ObservationRegistry`)이나 로거 출력 대신 인메모리 `ObservationLog`로 단순화했다 - 관찰 결과를 테스트에서 직접 조회할 수 있게 하려는 의도적 선택이다(8번 참고). exclude-paths의 우선순위 조합(예: 여러 패턴이 겹칠 때)이나 `@ConfigurationProperties` 유효성 검증(`@Validated`)도 다루지 않았다 - 이번 주의 초점(구조 조립)에서 벗어난다고 판단했다.

## 11. Spring 설계 의도

- **왜 `core`/`autoconfigure`/`starter`를 분리하는가**: `core`를 Boot 자동 설정 의존성 없이 순수하게 유지하면, 이 로직을 Boot 없이 쓰는 사용자(예: 순수 Spring MVC 프로젝트)도 재사용할 수 있고, 무엇보다 **단위 테스트가 `ApplicationContextRunner` 같은 무거운 도구 없이 그냥 객체를 `new`해서** 가능해진다 - 실제로 `RequestObservationInterceptorTest`는 컨텍스트를 전혀 띄우지 않는다. `autoconfigure`와 `starter`를 나누는 것은, "이 로직을 자동으로 켜고 싶다"(autoconfigure만 필요)와 "이 기능 전체를 한 번에 갖다 쓰고 싶다"(starter까지 필요)라는 서로 다른 사용자 요구를 분리하기 위해서다.
- **왜 `ObjectProvider`가 `@ConditionalOnMissingBean`이 걸린 빈을 참조하는 표준 수단인가**: 조건부로 존재하는 빈을 생성자 필수 인자로 받으면, "이 빈을 의존하는 다른 빈"의 존재 자체가 그 조건에 암묵적으로 종속돼 버린다 - 조건이 하나 바뀌면 전혀 관계없어 보이는 다른 빈까지 깨질 수 있다. `ObjectProvider`는 "있을 수도 없을 수도 있다"는 사실을 타입 시스템에 명시적으로 드러내고, 그 불확실성을 다루는 책임을 호출자(`ifAvailable`)에게 지역적으로 위임한다 - 9주차에서 본 `Optional`/`List`/`ObjectProvider`의 "관용적 처리"가 여기서는 "조건부 자동 설정과 안전하게 조합하는 관용구"로 다시 나타난다.
- **왜 스타터 모듈에는 정말 코드가 한 줄도 없어야 하는가**: 스타터에 로직이 조금이라도 들어가면, 그 로직은 `starter`를 의존하는 모든 프로젝트에 강제로 딸려 온다 - 로직을 바꾸려면 스타터 버전을 올려야 하고, 자동 설정만 재사용하고 싶은 사용자는 원치 않는 코드까지 받는다. "의존성 선언 그 자체가 유일한 산출물"이라는 제약은, 스타터가 언제까지나 순수하게 "무엇을 함께 가져올지"를 표현하는 선언적 문서로만 남게 강제한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `api`/`implementation` 구분이 없는 상태에서 멀티모듈 스타터를 만들다가 빌드가 바로 실패했다 - 16주 핵심 과정 내내 이 저장소가 단일 계층 모듈(각 실험이 서로 의존하지 않음)만 다뤄서 마주친 적 없던 문제를, 처음으로 진짜 멀티모듈 라이브러리를 만들면서 겪었다.
- 예상 밖이었던 것: `@EnableAutoConfiguration`만으로 임베디드 서버 없이도 Spring Boot의 표준 웹 자동 설정 전체가 문제없이 동작했다는 것 - 18주차에서 "자동 설정도 결국 `@Import` 하나일 뿐"이라고 결론 내렸던 것이, 이번엔 훨씬 복잡한 실제 조합(표준 자동 설정 + 우리 자동 설정 + `MockMvc`)에서도 그대로 성립한다는 것을 확인했다.
- 예상대로였던 것(설계 단계에서 미리 반영): `@ConditionalOnMissingBean`이 걸린 빈을 필수 의존성으로 받으면 위험하다는 것 - 19주차에서 배운 것을 실수로 겪기 전에 `ObjectProvider`로 미리 방어했다. "배운 것을 실전에 적용해서 겪을 뻔한 버그를 사전에 막았다"는 것 자체가, 이 저장소가 16주 동안 쌓아 온 방법론(공식 문서 → 최소 예제 → ... → 설계 의도 정리)이 실제로 작동한다는 증거다.
- **이걸로 로드맵의 20주 전체(핵심 16주 + 선택 4주)가 마무리된다.** [`retrospective/retrospective.md`](../retrospective/retrospective.md)는 핵심 16주만 다뤘으므로, 이번 4주(17~20주차, Spring Boot 내부)에서 확인한 것 - `SpringApplication`의 두 멀티캐스터, `SpringFactoriesLoader`/`.imports` 기반 SPI, `DeferredImportSelector`의 지연 처리, `Condition`의 단일 인터페이스 수렴, 그리고 이번 주의 3모듈 조립 - 은 핵심 16주가 확립한 원칙("정교한 판단보다 예측 가능한 순서", "확장점은 좁고 합성 가능하게 쪼갠다")이 Spring Boot 계층에서도 그대로, 오히려 더 명시적인 SPI 형태로 반복된다는 것을 보여준다.
