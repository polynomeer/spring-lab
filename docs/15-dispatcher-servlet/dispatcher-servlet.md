# DispatcherServlet 요청 처리 — Front Controller 하나가 나머지를 전부 떠넘기는 방식

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 15주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 24(DispatcherServlet Trace Application)·프로젝트 27(Mini Web MVC, 1~3단계)에 대응하는 분석 문서다.

## 1. 이번 질문

- Servlet Container와 Spring MVC의 경계는 어디인가? `DispatcherServlet`은 언제 생성되는가?
- 요청에 맞는 컨트롤러는 어떻게 찾는가? 후보가 여럿(리터럴 경로 vs 변수 경로, 여러 `HandlerMapping`)이면 무엇이 이기는가?
- `HandlerMapping`과 `HandlerAdapter`를 왜 분리했는가?
- 인터셉터는 정확히 어느 단계에서 실행되고, 어느 단계는 실행되지 않는가 — 특히 예외가 나거나 앞 인터셉터가 요청을 막았을 때?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Web MVC, "DispatcherServlet")은 `DispatcherServlet` 자체가 `HttpServlet`을 확장한 평범한 서블릿이며, 서블릿 컨테이너의 생명주기(`init()`) 안에서 `WebApplicationContext`를 만들거나 찾아 연결한다고 설명한다 — 즉 "Spring MVC가 시작되는 시점"은 "이 서블릿이 컨테이너에 의해 초기화되는 시점"과 같다.
- 같은 장은 `HandlerMapping`(요청 → 핸들러 찾기)과 `HandlerAdapter`(그 핸들러를 실제로 호출하기)가 별도의 확장점으로 분리돼 있고, 등록된 여러 `HandlerMapping`을 순서대로 검사해 첫 매칭을 채택한다고 명시한다.
- `HandlerInterceptor`의 `preHandle`/`postHandle`/`afterCompletion`이 "요청 처리 전/컨트롤러 실행 후·뷰 렌더링 전/전체 완료 후(예외 포함)"라는 서로 다른 시점에 호출된다고 설명하지만, 예외가 나거나 앞 인터셉터가 처리를 막았을 때 각 콜백이 정확히 어떻게 되는지는 소스를 봐야 알 수 있었다(6·9번에서 확인).

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@RestController`가 JSON을 반환하는 건 프레임워크에 내장된 기능이라 별도 라이브러리 없이도 동작할 거라 예상했다 — **틀렸다.** Jackson(`jackson-databind`)이 클래스패스에 없으면 `MappingJackson2HttpMessageConverter` 자체가 등록되지 않아서, `@RestController` 메서드조차 `406 Not Acceptable`/`415 Unsupported Media Type`으로 실패한다. 처음 테스트를 돌렸을 때 이 오류를 보고서야 깨달았다.
- `/users/{id}`와 `/users/me`가 둘 다 매핑돼 있으면, 나중에 등록된(또는 먼저 등록된) 것이 이길 거라 예상했다 — **틀렸다.** 등록 순서와 무관하게 **리터럴 경로가 항상 변수 경로보다 구체적인 것으로 취급**된다.
- 컨트롤러가 던진 `RuntimeException`은 Spring MVC가 알아서 500 응답으로 감싸 줄 거라 예상했다 — **틀렸다.** 기본 `DefaultHandlerExceptionResolver`는 Spring이 아는 특정 예외(`TypeMismatchException` 등)만 처리하고, 임의의 `RuntimeException`은 그대로 `doDispatch()`를 빠져나가 호출자에게 전파된다.
- 인터셉터 A가 통과시키고(preHandle=true) 인터셉터 B가 막으면(preHandle=false), A의 `afterCompletion`은 아예 호출 안 될 거라 예상했다 — **틀렸다.** 소스를 확인해 보니 A처럼 이미 성공적으로 `preHandle`을 통과한 인터셉터는 `afterCompletion`이 호출된다(6·9번).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/dispatcher-servlet-trace`](../../experiments/dispatcher-servlet-trace)
```java
@RestController
@RequestMapping("/users")
public class UserController {
    @GetMapping("/me")             // 리터럴 - 항상 우선
    public UserResponse me() { ... }

    @GetMapping("/{id}")           // 변수
    public UserResponse find(@PathVariable Long id, @RequestParam boolean detail) { ... }
}
```
```java
@Bean
public SimpleUrlHandlerMapping priorityHandlerMapping(HttpRequestHandler h) {
    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);   // RequestMappingHandlerMapping(order=0)보다 먼저
    mapping.setUrlMap(Map.of("/users/priority-test", h));
    return mapping;
}
```

**축소 구현** — [`mini-spring/mini-webmvc`](../../mini-spring/mini-webmvc)
```java
private Object findHandler(HttpServletRequest request) {
    for (HandlerMapping mapping : handlerMappings) {   // 실제 DispatcherServlet#getHandler와 동일한 구조
        HandlerMethod handler = mapping.getHandler(request);
        if (handler != null) return handler;
    }
    return null;
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `HttpServletBean` → `FrameworkServlet` → `DispatcherServlet` | 상속 계층 - 서블릿 설정 프로퍼티 바인딩 → `WebApplicationContext` 연결/생명주기 관리 → 실제 MVC 디스패치 |
| `DispatcherServlet#doDispatch` | 이번 주 실험의 핵심 - 핸들러 조회 → 인터셉터 `preHandle` → 실제 호출 → `postHandle` → 결과 처리, 예외는 전부 `dispatchException`으로 모아 나중에 처리 |
| `HandlerMapping` | "이 요청에 맞는 핸들러가 무엇인가" - `RequestMappingHandlerMapping`(애노테이션 기반), `SimpleUrlHandlerMapping`(URL 패턴 → 고정 핸들러) 등 여러 구현체가 동시에 등록될 수 있음 |
| `HandlerExecutionChain` | 핸들러 하나 + 그 핸들러에 적용되는 인터셉터 목록을 묶은 것 - `applyPreHandle`/`applyPostHandle`/`triggerAfterCompletion` 보유 |
| `HandlerAdapter` | "그 핸들러를 실제로 어떻게 호출하는가" - `RequestMappingHandlerAdapter`(`HandlerMethod`), `HttpRequestHandlerAdapter`(`HttpRequestHandler`) 등 핸들러의 "형태"마다 다른 구현체 |
| `PathPattern`/`SPECIFICITY_COMPARATOR` | 여러 경로 패턴이 매칭될 때 "더 구체적인" 쪽을 고르는 기준 - 리터럴 세그먼트가 변수 세그먼트보다 낮은(=더 구체적인) 점수를 받음 |
| (mini) `MiniDispatcherServlet` | `HttpServlet` 상속, `HandlerMapping`/`HandlerAdapter` 리스트를 순서대로 훑는 Front Controller |
| (mini) `AnnotationHandlerMapping` | `(HTTP 메서드, 경로)` 문자열의 완전 일치만 지원 - 패턴/우선순위 로직은 이번 주 범위 밖 |

## 6. 호출 흐름

```text
Servlet Container
  → HttpServlet#service(request, response)                  (jakarta.servlet 표준 진입점)
    → FrameworkServlet#service → processRequest              (WebApplicationContext를 요청 스레드에 바인딩)
      → DispatcherServlet#doService → doDispatch
          → getHandler(request)                              (여러 HandlerMapping을 순서대로 훑어 첫 매칭 채택)
              매칭 없음 → noHandlerFound → response.sendError(404)  (throwExceptionIfNoHandlerFound=false가 기본값)
          → getHandlerAdapter(handler)                        (핸들러의 "형태"에 맞는 HandlerAdapter 선택)
          → mappedHandler.applyPreHandle()                    (인터셉터 0→N 순서, 하나라도 false면 즉시 중단)
              false 반환 시: 이미 통과한 인터셉터들만 afterCompletion 호출하고 return
          → ha.handle(request, response, handler)             (실제 컨트롤러 메서드 호출 지점)
              예외 발생 시: dispatchException에 담기고 postHandle은 건너뜀
          → mappedHandler.applyPostHandle()                   (인터셉터 N→0 역순 - 11주차 AOP 인터셉터 체인과 같은 양파 구조)
          → processDispatchResult (뷰 렌더링 또는 예외 처리)
      → (dispatchException이 남아 있고 아무 ExceptionResolver도 처리 못 하면) 그대로 재던짐
```

인터셉터 preHandle 중단 시점과 리터럴/변수 경로 우선순위를 함께 그린 다이어그램: [`diagrams/dispatcher-flow.md`](diagrams/dispatcher-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 카탈로그(프로젝트 24)가 제시한 지점과 동일하다.

```text
jakarta.servlet.http.HttpServlet#service
org.springframework.web.servlet.FrameworkServlet#processRequest
org.springframework.web.servlet.DispatcherServlet#doDispatch
org.springframework.web.servlet.DispatcherServlet#getHandler
org.springframework.web.servlet.HandlerExecutionChain#applyPreHandle
org.springframework.web.servlet.HandlerExecutionChain#applyPostHandle
```

## 8. 런타임 관찰

[`DispatcherServletTraceTest`](../../experiments/dispatcher-servlet-trace/src/test/java/lab/experiments/mvc/DispatcherServletTraceTest.java) (9개, `MockMvc.webAppContextSetup` - 진짜 `DispatcherServlet` 인스턴스를 `MockServletContext`에 바인딩):

| 실험 | 결과 |
| --- | --- |
| `@RestController` 메서드 | JSON으로 직렬화(Jackson 필요) |
| `@Controller` 메서드(뷰 이름 반환) | `ModelAndView`로 처리 - `view().name(...)`/`model().attribute(...)`로 검증 가능, 본문 직렬화 경로 자체가 다름 |
| `/users/{id}` vs `/users/me` | 리터럴 경로가 항상 우선 매칭 |
| `@RequestBody`로 역직렬화 후 `ResponseEntity`로 반환 | 정상 동작 |
| 존재하지 않는 URL | 예외 없이 바로 404 |
| 컨트롤러가 `RuntimeException`을 던짐 | 처리할 `ExceptionResolver`가 없어 예외가 그대로 전파(`MockMvc.perform()`이 던짐) |
| 전역 인터셉터(경로 제한 없음) | `preHandle → postHandle → afterCompletion` 순서로 정확히 1번씩 |
| `preHandle=false`인 인터셉터가 특정 경로에만 등록됨 | 그 경로 요청 시 컨트롤러가 아예 호출되지 않음 |
| `HIGHEST_PRECEDENCE`로 등록한 `SimpleUrlHandlerMapping` | 같은 경로에 매핑된 `RequestMappingHandlerMapping`(기본 order)보다 먼저 선택됨 |

[`MiniDispatcherServletTest`](../../mini-spring/mini-webmvc/src/test/java/lab/minispring/webmvc/MiniDispatcherServletTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| 정확히 일치하는 `(GET, 경로)` | 등록된 컨트롤러 메서드 호출, 반환값이 응답 본문에 그대로 쓰임 |
| `HttpServletRequest`를 받는 메서드 | 프록시 기반 fake 요청 객체가 그대로 전달됨 |
| 매칭 없는 경로 | 404 |
| 컨트롤러 메서드가 예외를 던짐 | 500 |
| 같은 경로를 매핑하는 두 `HandlerMapping` | 먼저 등록된 쪽이 채택, 두 번째는 아예 검사되지 않음 |

**직접 겪은 것**: 처음 `@RestController` 테스트를 돌렸을 때 200이 아니라 406/415가 나왔다 — Jackson이 클래스패스에 없어서였다. 고치고 나니 이번엔 인터셉터 순서 테스트에서 `postHandle`이 빠졌는데, 원인은 같았다(같은 요청이 JSON 직렬화 실패로 예외 경로를 탔던 것) - 하나의 누락된 의존성이 서로 무관해 보이는 두 테스트를 동시에 깨뜨린 사례였다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스로 확인했다.

- **`HandlerExecutionChainTests#earlyExitInPreHandle()`**(`spring-webmvc`): 인터셉터 2개 중 첫 번째가 `preHandle=true`, 두 번째가 `preHandle=false`를 반환하면, `applyPreHandle()`이 즉시 중단되면서도 **첫 번째 인터셉터의 `afterCompletion`은 호출됨**을 `verify()`로 확인한다 — 우리 `preHandleReturningFalseNeverReachesTheController` 테스트는 "컨트롤러가 호출 안 됨"만 확인했는데, 이 공식 테스트는 그보다 한 단계 더 들어가 "이미 통과한 인터셉터의 뒷정리는 그래도 실행된다"는 것까지 검증한다 — 소스 분석(6번)에서 예측했던 것과 정확히 일치한다.
- **`RequestMappingInfoHandlerMappingTests#getHandlerBestMatch()`**: 여러 매핑 후보 중 요청 파라미터 조건이 더 구체적인 것이 선택되는 "best match" 메커니즘을 검증한다 — 리터럴 vs 변수 경로를 직접 검증하는 이름의 테스트는 아니지만, "후보가 여럿이면 더 구체적인 것을 고른다"는 같은 원리를 확인한다. **리터럴/변수 경로 우선순위 자체를 직접 검증하는 공식 테스트는 찾지 못했다** — `PathPattern.SPECIFICITY_COMPARATOR`(4·5번에서 인용) 소스로 확인했다.
- **`@RestController`가 Jackson 없이 실패하는 것, `DefaultHandlerExceptionResolver`가 임의의 `RuntimeException`을 처리하지 않는 것을 직접 검증하는 공식 단위 테스트는 찾지 못했다** — 정직하게 밝혀 둔다. 전자는 우리가 직접 겪은 런타임 오류로, 후자는 `DefaultHandlerExceptionResolver`가 처리하는 예외 타입 목록이 소스에 명시적으로 한정되어 있다는 것으로 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-webmvc`(project 27) — 이번 주에 1~3단계(Front Controller, HandlerMapping, HandlerAdapter)를 구현했다. 새 모듈로 만들되 `mini-container`/`mini-aop`에 의존하지 않는다 — Servlet API 위에서 동작하는 순수한 요청 처리 파이프라인이라, 빈 생성이나 AOP 프록시와는 독립적인 관심사이기 때문이다.

**구현한 것**
- `MiniDispatcherServlet`: `HttpServlet`을 상속하는 진짜 Front Controller - `HandlerMapping`/`HandlerAdapter` 리스트를 순서대로 훑는 구조가 실제 `DispatcherServlet#getHandler`와 거의 동일하다
- `HandlerMapping`/`HandlerAdapter` 인터페이스 분리 - "찾기"와 "호출하기"라는 서로 다른 책임
- `AnnotationHandlerMapping`: `@MiniRequestMapping`을 스캔해 `(메서드, 경로)` 완전 일치로 라우팅
- 테스트용 `HttpServletRequest`/`HttpServletResponse` fake를 `java.lang.reflect.Proxy`로 직접 구현(11주차 JDK Dynamic Proxy 실험과 같은 기법) - 임베디드 서블릿 컨테이너나 Spring의 Mock 객체 없이도 디스패처 로직만 순수하게 검증

**생략한 것 (의도적)**
- **경로 패턴/변수 매칭이 없다** - `/users/{id}`처럼 세그먼트를 변수로 추출하는 기능은 다음 주(16주차, ArgumentResolver)로 미뤘다. 지금은 문자열 완전 일치만 지원한다.
- **인터셉터(`HandlerInterceptor` 상당)가 없다** - `preHandle`/`postHandle`/`afterCompletion` 개념 자체가 아직 없다. `HandlerExecutionChain`에 해당하는 타입도 만들지 않았다.
- **리터럴 vs 변수 경로 같은 specificity 비교가 없다** - 애초에 패턴 매칭이 없으니 그 우선순위 문제 자체가 발생하지 않는다. 여러 `HandlerMapping` 사이의 우선순위(등록 순서)만 재현했다.
- **뷰 렌더링/`ModelAndView`가 없다** - 반환값을 그냥 `toString()`으로 응답에 쓴다. `ReturnValueHandler`(16주차)가 이 자리를 대체할 예정이다.

## 11. Spring 설계 의도

- **왜 `HandlerMapping`과 `HandlerAdapter`를 분리했는가**: "이 요청에 맞는 핸들러가 무엇인가"(라우팅 규칙)와 "그 핸들러를 실제로 어떻게 실행하는가"(호출 방식)는 서로 독립적으로 바뀔 수 있는 관심사다. 핸들러의 형태는 애노테이션 기반 `HandlerMethod`, 함수형 `HttpRequestHandler`, 레거시 `Controller` 인터페이스 등 다양한데, 이 다양성을 `HandlerMapping` 쪽에 흡수시키지 않고 `HandlerAdapter`라는 별도 확장점으로 분리한 덕분에 새로운 종류의 핸들러가 추가돼도 기존 `HandlerMapping` 구현체는 전혀 손댈 필요가 없다. 우리 mini 구현에서도 `HandlerMapping`(라우팅)과 `HandlerAdapter`(호출)를 분리해 두었기 때문에, 나중에 다른 형태의 핸들러가 생겨도 `AnnotationHandlerMapping`은 그대로 두고 새 `HandlerAdapter`만 추가하면 된다.
- **왜 여러 `HandlerMapping`을 동시에 등록할 수 있게 했는가**: 애노테이션 기반 라우팅(`@RequestMapping`)과 정적 URL 매핑(`SimpleUrlHandlerMapping`), 레거시 컨트롤러 인터페이스 기반 라우팅이 한 애플리케이션 안에 공존해야 하는 경우가 실무에서 드물지 않다. 이를 하나의 거대한 `HandlerMapping` 구현체로 합치는 대신, 여러 개를 등록하고 `order`로 우선순위만 정하게 한 것은 각 라우팅 전략을 독립적으로 추가·제거할 수 있게 하려는 설계다.
- **왜 리터럴 경로가 항상 변수 경로를 이기는가**: 사용자가 `/users/me`와 `/users/{id}`를 동시에 매핑했다면, 그 의도는 거의 항상 "me는 특별 케이스로 먼저 처리하고 싶다"는 것이다. 만약 등록 순서에 따라 결과가 달라진다면, `@Configuration`/컴포넌트 스캔 순서가 바뀔 때마다 라우팅 결과가 바뀌는 매우 취약한 시스템이 된다. `PathPattern`이 "리터럴 세그먼트 개수가 많을수록 더 구체적"이라는 고정된 규칙으로 specificity를 계산하는 것은, 라우팅 결과를 등록 순서라는 우연에 맡기지 않고 패턴 자체의 구조로 결정하기 위한 설계다.
- **왜 예외가 나면 `postHandle`은 건너뛰지만 이미 통과한 인터셉터의 `afterCompletion`은 실행하는가**: `postHandle`은 "정상적으로 만들어진 `ModelAndView`를 뷰 렌더링 직전에 조작할 기회"라는 명확한 의미를 갖는다 - 컨트롤러 실행이 실패해서 애초에 `ModelAndView`가 없다면 이 콜백은 의미가 없다. 반면 `afterCompletion`은 "이 요청 처리에 관여했던 자원을 정리할 마지막 기회"라는 의미이므로, `preHandle`을 통과해서 이미 무언가를 시작했을 수 있는 인터셉터라면 성공이든 실패든 반드시 뒷정리 기회를 줘야 한다 - 트랜잭션의 `finally`와 같은 역할이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@RestController`의 JSON 응답이 프레임워크에 내장된 게 아니라 Jackson 같은 외부 라이브러리에 의존한다는 것 - 없으면 조용히 실패하는 게 아니라 406/415로 명확하게 실패하지만, 그 원인이 "라이브러리 누락"이라는 걸 알아채는 데는 한 박자 걸렸다.
- 예상 밖이었던 것: 리터럴 경로가 등록 순서와 무관하게 항상 이긴다는 것, 그리고 preHandle이 중간에 막혀도 이미 통과한 인터셉터의 afterCompletion은 실행된다는 것 - 둘 다 "인터셉터/라우팅은 등록한 순서대로만 동작한다"는 단순한 직관을 깨는 지점이었다.
- 예상대로였던 것: `HandlerMapping`/`HandlerAdapter` 분리, 매칭 실패 시 예외 없이 바로 404, 처리되지 않은 컨트롤러 예외가 그대로 전파되는 것 - 레퍼런스 문서에 설명된 그대로 재현됐다.
- Mini 구현이 보여준 것: `DispatcherServlet#getHandler`의 핵심 로직(여러 `HandlerMapping`을 순서대로 훑어 첫 매칭 채택)은 겨우 5줄짜리 반복문이다 - 이번 주 실험에서 본 복잡성(경로 specificity, 인터셉터 콜백 타이밍, 예외 처리)은 대부분 `HandlerMapping`/`HandlerAdapter`의 **구현체**들과 `HandlerExecutionChain`이 만들어 내는 것이지, Front Controller 자체의 구조는 놀랍도록 단순하다.
- 다음 주로 이어지는 질문: 이번 주는 "핸들러를 찾고 호출한다"까지만 다뤘다 - 컨트롤러 메서드의 파라미터가 어떻게 채워지는지(`@PathVariable`/`@RequestBody`), 반환값이 어떻게 HTTP 응답으로 바뀌는지(`HttpMessageConverter`), 처리되지 않은 예외를 우아하게 잡아내는 방법(`@ExceptionHandler`)은 16주차(컨트롤러 메서드 호출과 응답 변환)로 이어진다.
