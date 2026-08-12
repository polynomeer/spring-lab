# 컨트롤러 메서드 호출과 응답 변환 — 확장점이 두 갈래로 갈라지는 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 16주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 25(Custom Argument Resolver)·26(Custom Return Value Handler)·27(Mini Web MVC, 4~6단계)에 대응하는 분석 문서다. 이 문서로 핵심 16주 과정의 코드/문서 산출물이 마무리된다.

**후기**: 10번 절은 원래 "JSON 역직렬화가 없다 - `@MiniRequestBody`는 원문 문자열만 바인딩한다"를 의도적 생략으로 남겨 뒀었다(`docs/retrospective/retrospective.md` 7번 절 "남겨 둔 질문"). mini-aop의 CGLIB 상당 서브클래스 프록시, mini-transaction의 `NESTED` 전파를 채운 뒤 그 생략도 채웠다 - `mini-spring/mini-webmvc`에 record 전용 `MiniJsonReader`를 추가하고 `RequestBodyArgumentResolver`가 파라미터 타입(`String` vs record)에 따라 원문 문자열 바인딩과 JSON 역직렬화 중 하나를 고르도록 바꿨다. 8·10·11·12번 절에 그 내용을 반영했다.

## 1. 이번 질문

- 컨트롤러 메서드의 파라미터는 어떻게 실제 값으로 채워지는가 - 여러 `ArgumentResolver` 후보가 있으면 무엇이 이기는가?
- 반환값을 HTTP 응답으로 바꾸는 확장점이 왜 하나가 아니라 여러 개(`HandlerMethodReturnValueHandler`, `ResponseBodyAdvice`, `HttpMessageConverter`)인가 - 그 책임 차이는 무엇인가?
- 처리되지 않은 예외를 우아하게 잡아내는 `@ExceptionHandler`는 어느 범위(같은 컨트롤러? 전역?)까지, 어떤 순서로 후보를 찾는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Web MVC, "Method Arguments"/"Return Values")은 `@RequestParam`/`@PathVariable`/`@RequestBody`/`@ModelAttribute` 등 각 애노테이션이 사실 개별 `HandlerMethodArgumentResolver` 구현체 하나씩에 대응한다고 설명한다 - 애노테이션 자체는 아무 로직도 갖지 않고, 그 애노테이션을 인식하는 리졸버가 실제 값을 채운다.
- 같은 장은 `@ResponseBody`/`ResponseEntity` 반환값이 `HandlerMethodReturnValueHandler`(반환값 처리 여부와 방식 결정) → `HttpMessageConverter`(실제 직렬화)의 2단계로 나뉘고, `ResponseBodyAdvice`는 그 사이(직렬화 직전)에 끼어들어 본문을 수정할 수 있는 별도의 확장점이라고 설명한다.
- `@ExceptionHandler`는 "먼저 예외를 던진 컨트롤러 자신의 클래스에서 찾고, 없으면 등록된 `@ControllerAdvice` 빈들에서 찾는다"는 우선순위를 명시한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `HandlerMethodReturnValueHandler`와 `ResponseBodyAdvice`가 결국 같은 목적(응답 본문을 원하는 대로 바꾸기)을 위한 거의 동등한 두 가지 선택지일 거라 예상했다 - **틀렸다.** 직접 만든 `HandlerMethodReturnValueHandler`가 메시지 변환을 스스로 처리하려고 `RequestResponseBodyMethodProcessor`를 "기본" 생성자로 새로 만들면, 그 javadoc이 명시하듯 **`ResponseBodyAdvice`가 전혀 적용되지 않는다** - 즉 전역으로 등록해 둔 `ResponseBodyAdvice`를 이 경로는 조용히 우회한다.
- 인자 해석기가 여러 개 있고 그중 두 개가 같은 파라미터를 지원할 수 있으면, Spring이 "더 구체적인" 것을 골라 줄 거라 예상했다 - **틀렸다.** `HandlerMethodArgumentResolverComposite`는 단순히 **먼저 등록된 리졸버**를 채택한다 - 우리가 mini에서 이미 여러 `HandlerMapping`/`HandlerAdapter`에 적용한 것과 똑같은 "등록 순서" 원칙이다.
- `@ExceptionHandler`가 이 컨트롤러/저 컨트롤러 가리지 않고 예외 타입만 보고 전역적으로 매칭될 거라 예상했다 - **틀렸다.** 같은 컨트롤러 클래스에 있는 `@ExceptionHandler`가 항상 `@ControllerAdvice`보다 먼저 검사된다.

## 4. 최소 재현 코드

**실제 Spring** — [`spring-extensions/api-response-handler`](../../spring-extensions/api-response-handler)
```java
public ApiResponseReturnValueHandler() {
    // "기본" 생성자 - javadoc: "Suitable for resolving @RequestBody and handling
    // @ResponseBody without RequestBodyAdvice or ResponseBodyAdvice."
    this.delegate = new RequestResponseBodyMethodProcessor(List.of(new MappingJackson2HttpMessageConverter()));
}
```
```java
@ControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    public Object beforeBodyWrite(Object body, ...) {
        return ApiResponse.of(body);   // 기존 메시지 변환 파이프라인 "안"에서 끼어듦
    }
}
```

**축소 구현** — [`mini-spring/mini-webmvc`](../../mini-spring/mini-webmvc)
```java
private Object[] resolveArguments(Method method, HttpServletRequest request) throws Exception {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
        args[i] = resolveArgument(parameters[i], request);   // 실제 getMethodArgumentValues와 같은 구조
    }
    return args;
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `RequestMappingHandlerAdapter` | `ArgumentResolver`/`ReturnValueHandler` 목록을 **소유** - `DispatcherServlet`이 아니라 이 어댑터에 등록된다 |
| `InvocableHandlerMethod#getMethodArgumentValues` | 파라미터마다 지원하는 리졸버를 찾아 값 하나씩 채움 - 없으면 `IllegalStateException("No suitable resolver")` |
| `HandlerMethodArgumentResolverComposite` | 여러 리졸버를 순서대로 검사, 파라미터별로 어떤 리졸버가 매칭됐는지 캐싱 |
| `HandlerMethodReturnValueHandler` | "이 반환값을 어떻게 처리할까"(뷰 이름? `@ResponseBody`? `ResponseEntity`?) 결정 |
| `ResponseBodyAdvice` | `@ResponseBody` 처리 파이프라인 **내부**에서 직렬화 직전 본문을 가로채는 별도 확장점 - `RequestResponseBodyAdviceChain`으로 여러 개가 체이닝됨 |
| `RequestResponseBodyMethodProcessor` | `@RequestBody`/`@ResponseBody`를 함께 처리 - 생성자에 advice 목록을 넘기지 않으면 advice 자체가 비활성화됨 |
| `ExceptionHandlerExceptionResolver` | 예외를 던진 컨트롤러의 클래스에서 먼저 `@ExceptionHandler`를 찾고, 없으면 `@ControllerAdvice` 빈들에서 찾음 |
| (mini) `MiniArgumentResolver`/`MiniReturnValueHandler` | 실제 타입과 이름·역할 대응 - 캐싱은 생략 |
| (mini) `AnnotationExceptionResolver` | 같은 빈 안에서만 `@MiniExceptionHandler`를 찾음 - `@ControllerAdvice` 상당 기능은 생략 |

## 6. 호출 흐름

```text
RequestMappingHandlerAdapter#invokeHandlerMethod
  → ServletInvocableHandlerMethod#invokeAndHandle
      → InvocableHandlerMethod#invokeForRequest
          → getMethodArgumentValues(request, mavContainer)
              각 MethodParameter마다:
                resolvers.supportsParameter(parameter)?
                  없음 → IllegalStateException("No suitable resolver")
                  있음(캐시 확인 후 처음 매칭된 것) → resolveArgument(...)
          → doInvoke(args)                              (리플렉션으로 실제 컨트롤러 메서드 호출)
      → 반환값을 returnValueHandlers에 위임
          handler.supportsReturnType(returnType)?
            @ResponseBody/ResponseEntity → RequestResponseBodyMethodProcessor
              → writeWithMessageConverters()
                  → advice.beforeBodyWrite(body, ...)    (등록된 ResponseBodyAdvice 체인, 있다면)
                  → 실제 HttpMessageConverter로 직렬화
            그 외(뷰 이름 등) → 다른 ReturnValueHandler가 ModelAndView 구성

(컨트롤러 메서드 실행 중 예외 발생 시)
DispatcherServlet#processDispatchResult → ExceptionHandlerExceptionResolver#resolveException
  → getExceptionHandlerMethod(handlerMethod, exception)
      1. handlerMethod가 속한 컨트롤러 클래스 자신의 @ExceptionHandler부터 검사
      2. 없으면 등록된 @ControllerAdvice 빈들을 검사
  → 매칭되면 그 메서드를 호출, 반환값을 다시 ReturnValueHandler 체인으로 처리
  → 아무도 못 찾으면 예외가 그대로 전파(15주차에서 확인한 것과 동일)
```

인자 해석과 반환값 처리 두 파이프라인, 그리고 `ResponseBodyAdvice`가 정확히 어디에 끼어드는지를 그린 다이어그램: [`diagrams/argument-return-flow.md`](diagrams/argument-return-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#invokeHandlerMethod
org.springframework.web.method.support.InvocableHandlerMethod#getMethodArgumentValues
org.springframework.web.method.support.HandlerMethodArgumentResolverComposite#resolveArgument
org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodProcessor#writeWithMessageConverters
org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver#getExceptionHandlerMethod
```

## 8. 런타임 관찰

[`CurrentUserArgumentResolverTest`](../../spring-extensions/current-user-argument-resolver/src/test/java/lab/ext/currentuser/CurrentUserArgumentResolverTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `@CurrentUser`(필수), 헤더 있음 | 정상 해석, `AuthenticatedUser` JSON 반환 |
| `@CurrentUser`(필수), 헤더 없음 | `ResponseStatusException` → 401 |
| `@CurrentUser(required=false)`, 헤더 없음 | `null`로 해석, 컨트롤러가 "anonymous"로 폴백 |
| `@CurrentUser(required=false)`, 헤더 있음 | 정상 해석 |

[`ApiResponseHandlerTest`](../../spring-extensions/api-response-handler/src/test/java/lab/ext/apiresponse/ApiResponseHandlerTest.java) (2개):

| 실험 | 결과 |
| --- | --- |
| `@WrapInApiResponse` 메서드(수동 `ReturnValueHandler`) | 정확히 한 번 래핑됨 - 이 경로는 `ApiResponseBodyAdvice`를 타지 않음(적용됐다면 이중 래핑) |
| 애노테이션 없는 메서드(기본 `@ResponseBody` 경로) | `ApiResponseBodyAdvice`가 적용되어 정확히 한 번 래핑됨 |

[`MiniDispatcherServletTest`](../../mini-spring/mini-webmvc/src/test/java/lab/minispring/webmvc/MiniDispatcherServletTest.java) (12개, 이번 주 5개 + 후기에서 추가한 JSON 역직렬화 2개):

| 실험 | 결과 |
| --- | --- |
| `@MiniPathVariable` + `@MiniRequestParam(required=false)` | 경로 변수와 쿼리 파라미터가 함께 해석되어 record JSON으로 직렬화 |
| 선택적 파라미터 누락 | `null` → 타입 변환 스킵, 컨트롤러가 `false`로 처리 |
| `@MiniRequestBody String` | 요청 본문 원문 문자열이 그대로 바인딩 |
| (후기) `@MiniRequestBody UserPayload`(record) | JSON 본문이 `MiniJsonReader`로 역직렬화되어 record로 바인딩, 그대로 다시 JSON으로 직렬화되어 응답 |
| (후기) `@MiniRequestBody UserWithAddressPayload`(중첩 record) | 중첩된 JSON 객체가 재귀적으로 역직렬화됨 |
| `MiniResponseEntity` 반환 | 지정한 상태 코드(201)로 응답 |
| 같은 컨트롤러의 `@MiniExceptionHandler` | 500 대신 그 메서드가 만든 응답으로 대체 |

(후기) [`MiniJsonReaderTest`](../../mini-spring/mini-webmvc/src/test/java/lab/minispring/webmvc/MiniJsonReaderTest.java) (10개, 파서 단위 테스트):

| 실험 | 결과 |
| --- | --- |
| 평범한 record, 필드 순서가 JSON과 다름 | 이름으로 매칭되므로 순서 무관하게 정상 바인딩 |
| 필드 누락 | 프리미티브 필드는 기본값(`0`/`false`)으로 채워짐 |
| 대상 record에 없는 알 수 없는 필드(스칼라/중첩 객체/배열) | 조용히 건너뜀 - Jackson의 기본값(실패)과 다른 의도적 선택 |
| 중첩 record | 재귀적으로 역직렬화 |
| 이스케이프 시퀀스(`\n`, `\uXXXX` 등) | 정확히 디코딩 |
| 객체를 record가 아닌 타입에 역직렬화 시도 | `IllegalArgumentException` |
| 중괄호가 안 닫힌 채 끝나는/값 뒤에 남는 문자가 있는 JSON | `IllegalArgumentException` |

**직접 겪은 버그**: `@MiniPathVariable`을 추가하면서 `/api/users/{id}`와 `/api/users/wrapped`를 같은 컨트롤러에 등록했는데, `Class#getMethods()`의 순회 순서가 보장되지 않아 `{id}` 패턴이 `wrapped()`보다 먼저 등록되는 경우 `id="wrapped"`로 해석되어 `NumberFormatException`(500)이 났다 - 15주차 real-Spring 실험에서 확인한 리터럴 vs 변수 경로 specificity 문제를 mini에서 그대로 재현한 것이었다. 변수 세그먼트 개수로 정렬해 리터럴 패턴을 먼저 검사하도록 고쳤다(10번에서 자세히).

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스로 확인했다.

- **`HandlerMethodArgumentResolverCompositeTests#checkArgumentResolverOrder()`**(`spring-web`): 같은 파라미터를 지원할 수 있는 리졸버 두 개를 등록하면 **먼저 등록된 것**이 채택된다는 것을 확인한다 - "더 똑똑하게 고르지 않고 등록 순서를 따른다"는 원칙이 `HandlerMapping`(15주차)과 `HandlerMethodArgumentResolverComposite`(이번 주)에 공통으로 적용된다는 것을 보여준다.
- **`HandlerMethodArgumentResolverCompositeTests#noSuitableArgumentResolver()`**: 지원하는 리졸버가 없으면 예외가 난다는 것을 확인한다 - 우리 mini의 `IllegalStateException("no ArgumentResolver for parameter: ...")`와 실제 `InvocableHandlerMethod`의 `IllegalStateException("No suitable resolver")`가 정확히 같은 실패 모드다.
- **`RequestResponseBodyAdviceChainTests#responseBodyAdvice()`**(`spring-webmvc`): `ResponseBodyAdvice.beforeBodyWrite()`가 실제로 본문을 가로채 바꿀 수 있다는 것을 Mock으로 검증한다 - 우리 `ApiResponseBodyAdvice`가 `UserResponse`를 `ApiResponse`로 감싸는 것과 정확히 같은 메커니즘이다.
- **같은 컨트롤러 우선 → `@ControllerAdvice` 폴백이라는 `@ExceptionHandler` 탐색 순서 자체를 직접 검증하는 공식 단위 테스트는 찾지 못했다** - 정직하게 밝혀 둔다. `ExceptionHandlerExceptionResolver#getExceptionHandlerMethod`(5·6번에서 인용한 소스)로 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-webmvc`(project 27) — 이번 주에 4~6단계(ArgumentResolver, ReturnValueHandler, ExceptionResolver)를 마무리해서 project 27을 완료했다.

**구현한 것**
- `MiniArgumentResolver` 체인: `@MiniPathVariable`, `@MiniRequestParam`(필수/선택), `@MiniRequestBody`(`String` 원문 또는 record JSON 역직렬화), 애노테이션 없는 `HttpServletRequest`
- `MiniReturnValueHandler` 체인: `String`, `MiniResponseEntity`(상태 코드 제어), 그 외 타입을 위한 리플렉션 기반 최소 JSON writer(`record`만 지원)
- `AnnotationExceptionResolver`: 예외를 던진 핸들러와 **같은 빈**에서 `@MiniExceptionHandler`를 찾아 대신 호출
- 경로 변수 추출을 위한 `AnnotationHandlerMapping`의 패턴 매칭 확장 + specificity 정렬(리터럴 우선)
- **(후기에서 추가) `MiniJsonReader`**: `MiniJsonWriter`의 반대편 - record 하나로만(중첩 record는 재귀로) 역직렬화하는 손으로 짠 재귀 하강 파서. `RequestBodyArgumentResolver`가 대상 파라미터 타입을 보고 `String`이면 원문을, record면 `MiniJsonReader.read(body, type)`의 결과를 넘긴다 - 실제 Spring이 `StringHttpMessageConverter`/`MappingJackson2HttpMessageConverter` 중 대상 타입에 맞는 컨버터를 고르는 지점과 같다.

**생략한 것 (의도적)**
- **리졸버 매칭 결과 캐싱이 없다** - 실제 `HandlerMethodArgumentResolverComposite`는 `Map<MethodParameter, HandlerMethodArgumentResolver>`로 한 번 찾은 결과를 캐싱해서 매 요청마다 리졸버 목록을 처음부터 다시 훑지 않는다. mini는 매번 처음부터 순회한다 - 정확성에는 영향 없지만 실제라면 요청마다 반복되는 비용이다.
- **`MiniJsonReader`는 record 전용이다** - 리스트/맵/제네릭/커스텀 역직렬화 규칙은 다루지 않는다(`MiniJsonWriter`가 record만 직렬화하는 것과 대칭). 알 수 없는 JSON 필드는 조용히 건너뛴다 - Jackson의 기본값(`FAIL_ON_UNKNOWN_PROPERTIES=true`)과 다른 의도적으로 관대한 선택이다. 실제 Jackson 기반 역직렬화는 이번 주 real-Spring 실험(project 25/26)에서 이미 다뤘다.
- **`ResponseBodyAdvice` 상당 확장점이 없다** - mini의 `MiniReturnValueHandler`는 실제 `HandlerMethodReturnValueHandler`와 `ResponseBodyAdvice`를 하나로 합친 형태다. 이 둘을 분리하지 않았으므로, project 26에서 확인한 "수동 처리기가 advice를 우회한다"는 함정 자체가 mini에는 없다(애초에 그 두 확장점이 분리돼 있지 않아서 생기지 않는 문제다).
- **`@ControllerAdvice` 상당(다른 컨트롤러의 예외까지 전역으로 처리)이 없다** - `AnnotationExceptionResolver`는 같은 빈 안에서만 찾는다. 실제 Spring의 2단계 탐색(로컬 우선 → 전역 폴백) 중 첫 단계만 구현했다.

## 11. Spring 설계 의도

- **왜 `ArgumentResolver`/`ReturnValueHandler`는 `DispatcherServlet`이 아니라 `RequestMappingHandlerAdapter`가 소유하는가**: `DispatcherServlet`은 핸들러의 "형태"를 몰라도 되는 자리다(15주차) - 반면 "이 컨트롤러 메서드의 파라미터를 어떻게 채우고 반환값을 어떻게 처리할지"는 `HandlerMethod`라는 특정 핸들러 형태에 강하게 결합된 지식이다. 이 지식을 `RequestMappingHandlerAdapter`(그 핸들러 형태를 아는 어댑터) 안에 가둬 둔 덕분에, 다른 형태의 핸들러(`HttpRequestHandler` 등)를 위한 다른 어댑터는 이 리졸버/핸들러 목록과 전혀 무관하게 독립적으로 존재할 수 있다.
- **왜 리졸버 우선순위가 "더 구체적인 것"이 아니라 "먼저 등록된 것"인가**: "구체성"을 자동으로 판단하려면 리졸버들 사이에 어떤 공통 기준(예: 애노테이션의 특정도, 타입의 특정도)이 있어야 하는데, 서로 다른 종류의 리졸버(애노테이션 기반, 타입 기반, 커스텀)를 하나의 기준으로 비교할 보편적인 방법이 없다. 대신 Spring은 "등록 순서가 곧 우선순위"라는 단순하고 예측 가능한 규칙을 택했다 - 사용자는 `WebMvcConfigurer#addArgumentResolvers()`에 원하는 순서로 커스텀 리졸버를 추가하기만 하면, 그 순서가 곧 우선순위가 된다는 것을 확신할 수 있다.
- **왜 `ResponseBodyAdvice`는 `HandlerMethodReturnValueHandler`와 별개의, 더 작은 확장점으로 분리돼 있는가**: `HandlerMethodReturnValueHandler`를 새로 만드는 것은 "이 반환 타입을 통째로 어떻게 처리할지"를 처음부터 다시 결정하는 무거운 작업이다(우리 `ApiResponseReturnValueHandler`가 메시지 변환기 목록까지 직접 구성해야 했던 것처럼). 반면 "이미 `@ResponseBody`로 처리되고 있는 응답의 본문만 살짝 바꾸고 싶다"는 훨씬 흔한 요구에는 그런 무거운 재구현이 필요 없어야 한다. `ResponseBodyAdvice`는 정확히 이 좁은 요구를 위해, 기존 처리 파이프라인 안에 끼어드는 더 가벼운 확장점으로 분리됐다 - 우리가 겪은 "수동 `ReturnValueHandler`는 advice를 우회한다"는 함정은, 이 분리를 정확히 이해하지 못하면 밟게 되는 자연스러운 발이다.
- **왜 `@ExceptionHandler`는 같은 컨트롤러를 `@ControllerAdvice`보다 먼저 검사하는가**: 컨트롤러 작성자가 자신의 클래스 안에 직접 `@ExceptionHandler`를 선언했다면, 그것은 "이 컨트롤러만의 특별한 처리가 필요하다"는 명시적 의도다. 전역 `@ControllerAdvice`가 이보다 먼저 개입한다면, 컨트롤러 작성자가 직접 선언한 처리 로직이 예상치 못하게 무시될 수 있다 - "더 지역적이고 구체적인 선언이 전역 규칙보다 우선한다"는 원칙은 이 문서 전체에서(리터럴 vs 변수 경로, self-invocation 등) 여러 번 등장한 것과 같은 종류의 설계 판단이다.
- **(후기에서 추가) 왜 `HttpMessageConverter` 선택은 대상 파라미터 타입을 보고 결정되는가**: `@RequestBody`가 붙은 파라미터가 "본문을 어떻게 해석해야 하는지"를 말해 주는 유일한 단서는 그 파라미터의 선언된 타입뿐이다 - 요청 헤더의 `Content-Type`은 실제로 그 값인지 신뢰할 수 없고(클라이언트가 틀리게 보낼 수 있다), 본문 자체를 먼저 파싱해 보지 않고는 형태를 알 수 없다. `RequestBodyArgumentResolver`가 `targetType == String.class`면 원문을, record면 `MiniJsonReader`를 고르는 것은 실제 `HttpMessageConverter` 목록에서 `canRead(type, mediaType)`이 대상 타입을 우선 기준으로 컨버터를 고르는 것과 같은 지점이다 - "무엇으로 변환할지"는 항상 목적지 타입이 결정한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 수동으로 만든 `HandlerMethodReturnValueHandler`가 전역 `ResponseBodyAdvice`를 조용히 우회한다는 것 - 두 확장점이 "같은 목적을 위한 대안"이 아니라 "서로 다른 층위에 있는, 합성 가능한 서로 다른 도구"라는 것을 실행해 보고서야 체감했다.
- 예상 밖이었던 것: 인자 해석기 우선순위도 결국 "등록 순서"라는 것 - 15주차의 `HandlerMapping` 우선순위, 이번 주의 `HandlerMethodArgumentResolverComposite` 우선순위가 전부 같은 원칙(정교한 판단 대신 예측 가능한 순서)을 공유한다는 것을 두 주에 걸쳐 확인했다.
- 예상대로였던 것(재확인): `@ExceptionHandler`가 같은 컨트롤러를 먼저 찾는다는 것, 반환값 처리가 "무엇을 할지 결정"과 "실제로 직렬화"의 2단계로 나뉜다는 것 - 레퍼런스 문서에 설명된 그대로였다.
- Mini 구현이 보여준 것: 경로 변수를 추가하자마자 15주차에서 다뤘던 리터럴/변수 경로 우선순위 문제가 그대로 재발했다 - 매주 배운 교훈이 다음 주 구현에서 그냥 사라지는 게 아니라, 새로운 기능을 추가할 때마다 실제로 다시 검증해야 하는 살아있는 제약이라는 것을 보여준 사례다.
- **이걸로 핵심 16주 과정(IoC 컨테이너 → 빈 생명주기 → 확장점 → 컴포넌트 스캔/DI → AOP → 트랜잭션 → Spring MVC)의 코드/문서 산출물이 마무리된다.** 선택 과정(17~20주차, Spring Boot 내부)으로 넘어가기 전에, 이번 16주간의 발견을 모으는 회고 문서를 별도로 정리할 수 있다.
- **(후기에서 추가) `MiniJsonReader`를 채우고 나서 다시 보니**: `MiniJsonWriter`(직렬화)와 `MiniJsonReader`(역직렬화)는 서로 거울 관계지만 코드 형태는 전혀 다르다 - writer는 `RecordComponent` 하나를 훑어 값을 문자열에 이어 붙이면 끝이지만, reader는 문자열 위치(`pos`)를 직접 관리하며 "지금 어떤 토큰을 기대하는가"를 스스로 추적하는 재귀 하강 파서가 필요했다. 직렬화가 "이미 존재하는 구조를 순회하며 읽어 내는" 문제인 반면 역직렬화는 "아직 존재하지 않는 구조를 문자 스트림에서 조립해 내는" 문제라서, 대칭적으로 보이는 두 기능의 구현 난이도가 실제로는 한쪽으로 크게 기운다는 것을 직접 짜 보고 체감했다.
