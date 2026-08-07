# Spring MVC 예외 처리 우선순위 — 세 리졸버, 그리고 하나가 조용히 삼킨 검증

[`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 28(Error Handling Pipeline)에 대응하는 분석 문서다. 15~16주차(`docs/15-dispatcher-servlet`, `docs/16-controller-invocation`)에서 다룬 `DispatcherServlet`/`HandlerAdapter` 인프라 위에서, 예외가 발생했을 때만 동작하는 별도의 처리 경로를 다룬다.

## 1. 이번 질문

- 같은 예외를 컨트롤러 자신의 `@ExceptionHandler`와 `@ControllerAdvice`가 둘 다 처리할 수 있으면 누가 이기는가?
- 여러 `@ControllerAdvice`가 있을 때 우선순위는 어떻게 정해지는가?
- `ResponseStatusException`과 `@ResponseStatus`가 붙은 커스텀 예외는 `@ExceptionHandler` 없이 어떻게 상태 코드를 결정하는가?
- 파라미터 타입 변환 실패, JSON 파싱 실패, Bean Validation 실패, 존재하지 않는 핸들러, 지원하지 않는 HTTP 메서드 - 이 다섯 가지는 전부 "예외"이지만 같은 리졸버가 처리하는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Web MVC, "Exceptions")은 기본으로 등록되는 `HandlerExceptionResolver` 체인이 `ExceptionHandlerExceptionResolver` → `ResponseStatusExceptionResolver` → `DefaultHandlerExceptionResolver` 순서라고 명시한다.
- `@ControllerAdvice`는 `@Order`(또는 `Ordered` 구현)로 우선순위를 정할 수 있다고 설명하며, 컨트롤러 자신의 `@ExceptionHandler`가 `@ControllerAdvice`보다 항상 먼저 고려된다고 명시한다.
- `ResponseStatusException`은 프로그래밍 방식으로 상태 코드를 지정하는 예외이고, `@ResponseStatus`는 예외 클래스 자체에 상태 코드를 선언하는 방식이라고 구분한다 - 둘 다 `@ExceptionHandler` 없이도 동작한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 컨트롤러 자신의 `@ExceptionHandler`와 `@ControllerAdvice`가 같은 예외를 처리할 수 있으면, 등록 순서(스캔 순서)에 따라 우연히 결정될 거라 예상했다 - **틀렸다.** `ExceptionHandlerExceptionResolver`는 컨트롤러 로컬 핸들러를 아예 별도의 캐시(`exceptionHandlerCache`)에서 **먼저** 조회하고, 매칭되면 advice 캐시는 쳐다보지도 않는다 - "우연"이 아니라 구조적으로 고정된 순서다.
- 여러 `@ControllerAdvice`가 같은 예외를 처리할 수 있으면 Spring이 예외를 던져 경고할 거라 예상했다 - **틀렸다.** 조용히 `@Order`가 더 작은 쪽이 이기고, 나머지는 그냥 호출되지 않는다 - "핸들러가 매칭될 수 있다"는 것과 "실제로 실행된다"는 것이 다르다.
- `hibernate-validator`를 클래스패스에 추가하기만 하면 `@Valid`가 바로 동작할 거라 예상했다 - **틀렸다.** EL(`jakarta.el`) 구현체가 없으면 `ValidatorFactory` 생성 자체가 실패하는데, Spring의 `OptionalValidatorFactoryBean`이 이 실패를 **조용히 삼키고 검증을 통째로 no-op으로 만들어 버린다** - 컨텍스트는 정상적으로 뜨고, 검증 실패를 기대한 요청이 그냥 200으로 통과해 버렸다.
- 카탈로그가 "존재하지 않는 Handler"를 별도 실험 항목으로 요구하길래, `throwExceptionIfNoHandlerFound`를 명시적으로 켜야 할 거라 예상했다 - **틀렸다(더 이상은).** Spring 6.1부터 기본값이 이미 `true`로 바뀌었고, 그 setter는 `@Deprecated(forRemoval = true)`다 - 카탈로그가 상정한 예전 동작은 이미 지나간 이야기였다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/mvc-exception-pipeline`](../../experiments/mvc-exception-pipeline)
```java
@RestController
@RequestMapping("/widgets")
public class DemoController {

    @GetMapping("/boom-local")
    public String boomLocal() { throw new LocalOnlyException("local failure"); }

    @ExceptionHandler(LocalOnlyException.class)  // 같은 컨트롤러 안 - 항상 먼저 조회됨
    public ResponseEntity<String> handleLocally(LocalOnlyException ex) { ... }
}

@ControllerAdvice
@Order(1)  // 값이 작을수록 먼저 조회됨 - 매칭되면 그 자리에서 확정
public class HighPriorityAdvice {
    @ExceptionHandler(SharedFailureException.class)
    public ResponseEntity<String> handle(SharedFailureException ex) { ... }
}
```

**축소 구현은 두지 않았다** — 이 주제는 "Spring이 이미 정해 둔 우선순위 규칙을 실측"하는 것이 핵심이라, 별도로 축소 재구현할 만한 새로운 추상화가 없다(10번 절 참고).

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `HandlerExceptionResolver` | "핸들러 실행 중 예외가 나면 어떻게 응답으로 바꿀지"를 담당하는 전략 인터페이스 - `HandlerMapping`/`HandlerAdapter`와 나란히 `DispatcherServlet`이 들고 있는 세 번째 확장 지점 |
| `HandlerExceptionResolverComposite` | 등록된 여러 리졸버를 순서대로 시도하는 컴포지트 - 하나가 `ModelAndView`(빈 것이라도)를 반환하면 그 자리에서 멈춘다 |
| `ExceptionHandlerExceptionResolver` | `@ExceptionHandler` 메서드(컨트롤러 로컬 + `@ControllerAdvice`)를 찾아 실행 |
| `ControllerAdviceBean` | `@ControllerAdvice` 빈을 감싸는 래퍼 - `findAnnotatedBeans()`가 `OrderComparator`로 정렬한 목록을 만든다 |
| `ExceptionHandlerMethodResolver` | 한 클래스(컨트롤러든 advice든) 안에서 특정 예외 타입에 맞는 `@ExceptionHandler` 메서드를 찾는 조회기 |
| `ResponseStatusExceptionResolver` | `ResponseStatusException`과 `@ResponseStatus`가 붙은 예외를 `@ExceptionHandler` 없이 상태 코드로 변환 |
| `DefaultHandlerExceptionResolver` | Spring MVC 내부 예외(타입 변환 실패, 파싱 실패, 검증 실패, 핸들러 없음, 메서드 불일치 등)를 표준 HTTP 상태 코드로 매핑하는 마지막 안전망 |
| `OptionalValidatorFactoryBean` | JSR-380 provider 설정이 실패하면 예외를 던지는 대신 검증을 no-op으로 만드는 `LocalValidatorFactoryBean`의 관대한 서브클래스 |

## 6. 호출 흐름

```text
핸들러(또는 인자 리졸버·컨버터) 실행 중 예외 발생
  → DispatcherServlet#processHandlerException
  → HandlerExceptionResolverComposite - 등록된 리졸버를 순서대로 시도

[1] ExceptionHandlerExceptionResolver
  → handlerMethod가 있으면: 그 컨트롤러 클래스 전용 exceptionHandlerCache 먼저 조회
    → 매칭되면 즉시 반환 (advice는 확인조차 안 함)
  → 없으면: exceptionHandlerAdviceCache(@Order로 이미 정렬된 LinkedHashMap)를
    순서대로 순회하며 첫 매칭에서 반환

[2] ResponseStatusExceptionResolver (위에서 처리 못 했을 때만 도달)
  → ResponseStatusException이면 그 안의 상태 코드/이유를 그대로 사용
  → 예외 클래스에 @ResponseStatus가 있으면 그 값을 사용
  → 둘 다 아니면 다음 리졸버로

[3] DefaultHandlerExceptionResolver (마지막 안전망)
  → MethodArgumentTypeMismatchException → 400
  → HttpMessageNotReadableException → 400
  → MethodArgumentNotValidException → 400
  → NoHandlerFoundException → 404 (throwExceptionIfNoHandlerFound, 6.1부터 기본 true)
  → HttpRequestMethodNotSupportedException → 405
```

세 리졸버가 같은 예외를 두고 갈라지는 지점을 시퀀스로 그린 다이어그램: [`diagrams/exception-resolution-order.md`](diagrams/exception-resolution-order.md)

## 7. 브레이크포인트

이번 주제는 소스 확인(2·9번)과 `MockMvc` 기반 런타임 관찰(8번)로 검증했다. 이후 학습 대시보드의
mvc-exception-priority 시나리오([`experiments/mvc-exception-pipeline`](../../experiments/mvc-exception-pipeline)의
`ExceptionPipelineLab`)를 만들며 실제 jdi-tracer 세션으로도 다시 확인했다 - 클래스/메서드 이름
자체는 정확했지만, 그 과정에서 문서에 없던 동작을 하나 발견했다: `ResponseStatusExceptionResolver
#doResolveException`은 예외 자신에게 `@ResponseStatus`가 없으면 `ex.getCause()`가 있는 한
**자기 자신을 재귀 호출**한다(소스: `if (ex.getCause() instanceof Exception cause) { return
doResolveException(request, response, handler, cause); }`) - 그래서 `MethodArgumentTypeMismatchException`처럼
원인 체인이 있는 예외 하나에 이 브레이크포인트가 여러 번(예: 바깥 예외 한 번 + 원인 예외 한 번)
걸린다. 각 히트의 `ex` 지역 변수 타입이 바뀌는 걸로 이 재귀를 그대로 관찰할 수 있다.

```text
org.springframework.web.servlet.DispatcherServlet#processHandlerException
org.springframework.web.servlet.handler.HandlerExceptionResolverComposite#resolveException
org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver#getExceptionHandlerMethod
org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver#doResolveException
org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver#doResolveException
```

## 8. 런타임 관찰

[`ExceptionPipelineTest`](../../experiments/mvc-exception-pipeline/src/test/java/lab/experiments/mvcerror/ExceptionPipelineTest.java) (10개, `MockMvc.webAppContextSetup` - 실제 `DispatcherServlet` 인스턴스 사용):

| 예외 발생 위치 | 처리 컴포넌트 | 최종 응답 |
| --- | --- | --- |
| 컨트롤러 로컬 `@ExceptionHandler`도 있고 `@ControllerAdvice`도 있음 | `ExceptionHandlerExceptionResolver`(로컬이 항상 먼저 이김) | 418 |
| 컨트롤러엔 핸들러 없음, `@ControllerAdvice` 하나만 매칭 | `ExceptionHandlerExceptionResolver`(advice) | 502 |
| `@ControllerAdvice` 두 개가 같은 예외를 처리할 수 있음(`@Order(1)` vs `@Order(2)`) | `ExceptionHandlerExceptionResolver`(order가 작은 쪽) | 409 |
| `ResponseStatusException`을 직접 던짐 | `ResponseStatusExceptionResolver` | 402 |
| `@ResponseStatus`가 붙은 커스텀 예외 | `ResponseStatusExceptionResolver` | 404 |
| `@PathVariable long`에 숫자가 아닌 값 | `DefaultHandlerExceptionResolver`(`MethodArgumentTypeMismatchException`) | 400 |
| `@RequestBody`에 깨진 JSON | `DefaultHandlerExceptionResolver`(`HttpMessageNotReadableException`) | 400 |
| `@Valid` 대상 필드가 `@NotBlank` 위반 | `DefaultHandlerExceptionResolver`(`MethodArgumentNotValidException`) | 400 |
| 존재하지 않는 URL | `DefaultHandlerExceptionResolver`(`NoHandlerFoundException`) | 404 |
| 매핑은 있으나 지원 안 하는 HTTP 메서드(`PUT`) | `DefaultHandlerExceptionResolver`(`HttpRequestMethodNotSupportedException`) | 405 |

**직접 겪은 버그 1**: `hibernate-validator`만 의존성에 추가하고 처음 테스트를 돌렸더니, `@NotBlank` 위반 요청이 400이 아니라 **200**으로 통과했다. `OptionalValidatorFactoryBean`을 단독으로 재현해 보니(`afterPropertiesSet()` 호출 후 직접 `validate()`), 로그에 `"Failed to set up a Bean Validation provider: ... Unable to initialize 'jakarta.el.ExpressionFactory'"`가 찍혔다 - `hibernate-validator`의 기본 메시지 보간기(`ResourceBundleMessageInterpolator`)가 EL 구현체 없이는 `ValidatorFactory` 생성 자체에 실패하는데, `OptionalValidatorFactoryBean.afterPropertiesSet()`의 소스를 보면 이 `ValidationException`을 **의도적으로** catch해서 로그만 남기고 삼킨다(Javadoc 원문: "simply turns Validator calls into no-ops in case of no Bean Validation provider being available"). 컨텍스트는 아무 오류 없이 뜨고, `@Valid`가 있는 파라미터는 그냥 조용히 검증되지 않는다 - `org.glassfish:jakarta.el`을 `runtimeOnly`로 추가해서 해결했다. "의존성을 추가했다"와 "그 의존성이 완전히 동작한다"가 다르다는 것을, 실패가 예외가 아니라 로그 한 줄로만 남는 방식으로 겪었다.

**직접 겪은 버그 2**: "존재하지 않는 Handler" 테스트를 위해 카탈로그의 통상적인 안내대로 `DispatcherServlet#setThrowExceptionIfNoHandlerFound(true)`를 `MockMvc`의 `addDispatcherServletCustomizer`로 명시적으로 켰는데, 컴파일러가 "deprecated and marked for removal" 경고를 냈다. 소스를 확인해 보니 6.1부터 기본값 자체가 `true`로 바뀌어 있었다 - 세터를 완전히 제거하고도 테스트가 그대로 통과했다. 카탈로그 문서 작성 시점(구버전 Spring 가정)과 지금 고정한 버전(6.2.19) 사이에 이미 지나간 변경이 있었다는 뜻이다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `ExceptionHandlerExceptionResolverTests`(`spring-webmvc`)로 확인했다.

- **로컬 핸들러가 advice보다 우선한다는 것**은 `getExceptionHandlerMethod()` 소스(517~570행, 6번 절에 인용)에서 `handlerMethod != null`인 분기가 advice 캐시 순회보다 먼저 나오고, 매칭 시 즉시 `return`한다는 것으로 직접 확인했다 - 이 정확한 시나리오(로컬 vs advice 경쟁)를 위한 전용 공식 테스트는 찾지 못했지만, 소스 구조 자체가 이미 그 순서를 강제한다.
- **`@ControllerAdvice` 정렬**은 `ControllerAdviceBean.findAnnotatedBeans()`(233~255행)의 `OrderComparator.sort(adviceBeans)` 호출로 확인했다.
- **`OptionalValidatorFactoryBean`이 `ValidationException`을 삼킨다는 것**은 클래스 자체의 소스(전체를 8번 절에 인용)로 확인했다 - 이건 "실패를 예외로 알리지 않고 로그로만 남기는" Spring의 드문 설계 선택이라, 별도의 공식 유닛 테스트보다는 클래스 Javadoc과 구현이 그 자체로 명세 역할을 한다.
- **`throwExceptionIfNoHandlerFound` 기본값 변경**은 `DispatcherServlet.setThrowExceptionIfNoHandlerFound()`의 `@Deprecated(since = "6.1", forRemoval = true)` 애노테이션과 Javadoc("as of 6.1 this property is set to true by default")으로 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

이번 주제는 **의도적으로 mini 재구현을 만들지 않았다.** 이유:

- 카탈로그의 다른 주제(빈 생명주기, AOP, 트랜잭션, 이벤트)는 "Spring이 어떤 추상화로 문제를 해결하는가"를 이해하기 위해 그 추상화를 직접 축소 재현하는 것이 학습에 도움이 됐다. 반면 예외 처리 우선순위는 새로운 추상화가 아니라, 이미 16주차 `mini-webmvc`에서 구현한 `HandlerAdapter`/`HandlerMethod` 파이프라인 위에 "여러 후보 중 어느 게 먼저 매칭되는가"라는 **정책**을 하나 더 얹는 문제다.
- `mini-webmvc`가 이미 리플렉션 기반 핸들러 호출 골격을 갖고 있으므로, "예외 발생 시 후보 메서드를 우선순위대로 조회한다"는 로직은 `ExceptionHandlerMethodResolver`의 캐시 조회 로직을 그대로 베끼는 것에 가까워 새로 배울 게 적다고 판단했다.
- 대신 이번 주제는 실제 Spring의 우선순위 규칙을 정확히 검증하는 것 자체에 집중했다 - "규칙을 재구현하는 것"보다 "규칙이 실제로 그런지 확인하는 것"이 이 주제의 핵심이라고 봤다.

## 11. Spring 설계 의도

- **왜 컨트롤러 로컬 `@ExceptionHandler`가 항상 `@ControllerAdvice`보다 우선하는가**: 컨트롤러 자신이 "이 예외는 내가 안다"고 선언한 것이므로, 전역 정책(advice)보다 그 컨트롤러의 국소적 지식이 더 구체적이고 신뢰할 수 있는 정보다. 이는 Java의 오버라이딩 규칙(더 구체적인 타입이 이긴다)과 같은 직관을 예외 처리에도 그대로 적용한 것이다.
- **왜 `HandlerExceptionResolver`를 세 개(어노테이션 기반/상태 코드 기반/내부 예외 기반)로 나눴는가**: 각 리졸버는 서로 다른 "예외의 출처"를 겨냥한다 - 사용자가 작성한 핸들러 메서드(`ExceptionHandlerExceptionResolver`), 사용자가 명시적으로 던진 상태 코드(`ResponseStatusExceptionResolver`), 그리고 Spring MVC 인프라 자체가 던지는 예외(`DefaultHandlerExceptionResolver`). 이렇게 나눈 덕분에 사용자 코드를 전혀 건드리지 않아도 마지막 리졸버가 항상 안전망 역할을 한다 - "사용자가 아무 예외 처리도 안 했다"는 상태가 곧바로 500이 아니라 각 예외에 맞는 표준 코드로 이어진다.
- **왜 `OptionalValidatorFactoryBean`은 검증 실패를 예외가 아니라 no-op으로 처리하는가**: 이 클래스의 존재 이유 자체가 "JSR-380 provider가 클래스패스에 없을 수도 있는" 상황을 감당하기 위한 것이다(이름 자체가 "Optional"). Bean Validation은 Spring MVC의 필수 기능이 아니라 선택적 기능이므로, provider 설정이 실패했다고 애플리케이션 전체가 뜨지 못하게 막는 것은 과하다 - 대신 "검증 기능이 없다"는 사실만 조용히 받아들이고 나머지는 정상 동작하게 한다. 다만 이 관대함이 우리가 겪은 것처럼 "설정 실수를 침묵시키는" 부작용을 함께 가진다는 점은 트레이드오프다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `hibernate-validator`를 추가하는 것과 `@Valid`가 실제로 동작하는 것 사이에 EL 구현체라는, 문서 어디에도 눈에 띄게 강조되지 않는 조건이 하나 더 있었다는 것 - 그리고 그 조건이 깨졌을 때 Spring이 실패가 아니라 침묵을 선택한다는 것. 의존성 목록만 봐서는 절대 알 수 없고, 실제로 검증이 통과해 버리는 것을 보고 나서야 의심할 수 있는 종류의 문제였다.
- 예상 밖이었던 것: 카탈로그가 상정한 "존재하지 않는 Handler를 위해 설정을 켜야 한다"는 전제 자체가 이미 최신 버전에서는 사실이 아니었다는 것 - 학습 자료(카탈로그)도 버전에 따라 낡을 수 있고, "공식 문서/소스를 직접 확인하라"는 이 저장소의 방법론(`docs/plan/00-methodology.md`)이 카탈로그 자체에도 예외 없이 적용돼야 한다는 걸 다시 확인했다.
- 새로운 추상화를 만들지 않기로 한 이번 판단 자체가 하나의 결론이다 - 모든 주제가 mini 재구현을 필요로 하는 것은 아니고, "이미 구현한 인프라 위에 정책 하나를 검증하는 것"과 "새 추상화를 배우기 위해 재구현하는 것"을 구분하는 게 이 방법론(`docs/plan/00-methodology.md`)이 경계하는 "축소 구현을 위한 축소 구현"을 피하는 길이었다.
