# @Async — 프록시로 돌아왔지만, 반환 타입이 예외 처리 경로 전체를 바꾼다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`28`](../28-scheduled-tasks/scheduled-tasks.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`28-scheduled-tasks`](../28-scheduled-tasks/scheduled-tasks.md)가 "프록시가 전혀 없는" 확장점이었다면, `@Async`는 다시 프록시로 돌아온다 — `AsyncAnnotationBeanPostProcessor`는 [`26-method-validation`](../26-method-validation/method-validation.md)의 `MethodValidationPostProcessor`와 같은 계열(`AbstractBeanFactoryAwareAdvisingPostProcessor`)이다. 그런데도 `@Scheduled`가 쓰던 `TaskExecutor` 인프라와 정면으로 대비되는 기본값(스레드 하나 공유 vs 호출마다 새 스레드)을 갖고 있고, 반환 타입(`void`/`Future`)에 따라 예외 처리 경로 자체가 완전히 갈라진다는 걸 확인한다.

## 1. 이번 질문

- `@Async` 메서드는 `MethodValidationInterceptor`(26번)처럼 같은 계열의 단순한 프록시 생성 경로를 쓰는가?
- `TaskExecutor` 빈을 등록하지 않으면 어떤 실행자로 폴백하는가 — `@Scheduled`(28번)와 같은가, 다른가?
- 반환 타입이 `void`인 메서드가 예외를 던지면 그 예외는 어디로 가는가?
- 반환 타입이 `Future`인 메서드가 예외를 던지면, 호출자가 `future.get()`으로 그 예외를 볼 수 있는가?
- self-invocation은 여기서도 우회되는가?
- `@EnableAsync`가 없으면 `@Async`는 어떻게 되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Task Execution and Scheduling", "Annotation Support for Scheduling and Async Execution")는 `@Async` 메서드의 반환 타입으로 `void`, `Future<V>`, `CompletableFuture<V>`, `ListenableFuture<V>`(사실상 6.2에서 지원 종료 절차 진행)를 명시한다.
- 문서는 "`void` 메서드의 예외는 호출자에게 전달될 수 없다 - `AsyncUncaughtExceptionHandler`를 등록하라"고 설명하고, `Future` 반환 메서드는 "예외가 `Future` 안에 담긴다"고 짧게만 언급한다.
- 기본 실행자에 대해서는 "`TaskExecutor` 빈이 있으면 그걸 쓰고, 없으면 기본값으로 폴백한다"고만 설명하고, 그 기본값의 정체(스레드 개수, 재사용 여부)는 명시하지 않는다 — 이번 실험은 소스로 직접 확인했다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Async`도 28번의 `@Scheduled`와 마찬가지로 프록시 없이 직접 리플렉션으로 실행될 거라 예상했다 — **틀렸다.** `@Async`는 프록시 기반이다 - `AsyncAnnotationBeanPostProcessor`가 대상 클래스에 `Advisor`를 붙여 프록시를 만든다.
- `TaskExecutor` 빈이 없으면 `@Scheduled`처럼 스레드 하나를 공유하는 실행자로 폴백할 거라 예상했다 — **정반대로 틀렸다.** `SimpleAsyncTaskExecutor`로 폴백하는데, 이건 풀링을 전혀 하지 않고 **호출마다 새 스레드를 만드는** 실행자다.
- `void` 메서드가 예외를 던지면 그냥 조용히 사라질(아무 데도 기록되지 않을) 거라 예상했다 — **틀렸다.** `AsyncUncaughtExceptionHandler`라는 명시적인 확장점으로 전달된다 - 기본 구현체는 로그만 남기지만, 그 경로 자체는 존재한다.
- `Future` 반환 메서드의 예외는 `Future` 안에 값처럼 얌전히 담겨 있다가 `get()` 호출 시점에만 드러날 거라 예상했다 — 대체로 맞았다. 다만 그 전달 방식이 "예외를 캐치해서 값으로 감싸는" 것이 아니라 **그대로 다시 던지는**(`ReflectionUtils.rethrowException`) 것이었다는 점은 예상 밖이었다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/async-method-lab`](../../experiments/async-method-lab)

```java
@Configuration
@EnableAsync
public class AsyncPlaygroundConfig implements AsyncConfigurer {
    @Bean
    public RecordingAsyncUncaughtExceptionHandler recordingAsyncUncaughtExceptionHandler() {
        return new RecordingAsyncUncaughtExceptionHandler();
    }
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return recordingAsyncUncaughtExceptionHandler();
    }
    // getAsyncExecutor()는 오버라이드하지 않는다 - 기본 폴백 경로를 그대로 관찰하기 위해서다.
}
```

```java
public interface AsyncTaskService {
    @Async CompletableFuture<String> recordThreadName();
    @Async void fireAndForgetThatThrows();
    @Async Future<String> returnsFutureThatThrows();

    default String refreshViaSelfInvocation() {
        return this.recordThreadName().join();   // this.xxx() - 프록시를 거치지 않음
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AsyncAnnotationBeanPostProcessor` | 26번의 `MethodValidationPostProcessor`와 같은 계열(`AbstractBeanFactoryAwareAdvisingPostProcessor`) - `@Async` 클래스/메서드에 고정된 `Advisor` 하나를 붙여 프록시를 만든다 |
| `AnnotationAsyncExecutionInterceptor` | 실제 `MethodInterceptor` - 반환 타입에 따라 `Callable`을 어떻게 제출할지(`doSubmit`), 예외를 어떻게 처리할지(`handleError`)를 결정 |
| `AsyncExecutionAspectSupport` | 실행자 결정(`getDefaultExecutor`) + 반환 타입별 제출 전략(`doSubmit`) + 예외 처리 위임(`handleError`)을 담당하는 상위 클래스 |
| `AsyncConfigurer` | 사용자가 기본 `Executor`/`AsyncUncaughtExceptionHandler`를 오버라이드할 수 있는 콜백 - 어느 한쪽만 오버라이드해도 나머지는 기본 폴백이 그대로 적용됨(`SingletonSupplier`의 primary-then-fallback 구조) |
| `SimpleAsyncTaskExecutor` | `TaskExecutor` 빈이 없을 때의 기본 폴백 - 풀링 없이 **매 제출마다 새 스레드**를 만듦 |
| `AsyncUncaughtExceptionHandler` | `void` 반환 메서드의 예외를 받는 SPI - 기본 구현체(`SimpleAsyncUncaughtExceptionHandler`)는 로그만 남김 |
| (실험) `RecordingAsyncUncaughtExceptionHandler` | 예외가 실제로 이 SPI까지 도달했는지 `CountDownLatch`로 계측 |

## 6. 호출 흐름

```text
프록시 메서드 호출
  → AnnotationAsyncExecutionInterceptor#invoke(invocation)
      → executor = determineAsyncExecutor(userMethod)
          → AsyncExecutionAspectSupport#getDefaultExecutor(beanFactory)
              → TaskExecutor 빈 조회 → 없으면 "taskExecutor"라는 이름의 Executor 빈 조회
              → 그래도 없으면 (AsyncExecutionInterceptor가 오버라이드) new SimpleAsyncTaskExecutor()
      → task = () -> {
            try {
                result = invocation.proceed()                    (대상 메서드 실제 실행)
                if (result instanceof Future<?> f) return f.get()
            } catch (Throwable ex) {
                handleError(ex, userMethod, args)
                    → 반환 타입이 Future 계열이면: ReflectionUtils.rethrowException(ex)  ← 그대로 재던짐
                    → 그 외(void 등)면: exceptionHandler.handleUncaughtException(ex, ...)  ← 삼키고 위임
            }
            return null
        }
      → doSubmit(task, executor, userMethod.getReturnType())
          → CompletableFuture 반환:  executor.submitCompletable(task)
          → Future 반환:             executor.submit(task)
          → void 반환:               executor.submit(task); return null   (호출자는 즉시 null을 받고 끝)
```

self-invocation(`this.recordThreadName()`)은 위 흐름 전체를 건너뛴다 - `AnnotationAsyncExecutionInterceptor`가 끼어들 프록시 경계 자체를 거치지 않기 때문이다(11·12·25·26번과 동일한 구조).

반환 타입별 예외 처리 경로와, `SimpleAsyncTaskExecutor`(호출마다 새 스레드)를 28번의 단일 스레드 폴백과 나란히 비교한 다이어그램: [`diagrams/async-exception-flow.md`](diagrams/async-exception-flow.md)

## 7. 브레이크포인트

25~28번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.aop.interceptor.AsyncExecutionInterceptor#invoke
org.springframework.aop.interceptor.AsyncExecutionInterceptor#getDefaultExecutor
org.springframework.aop.interceptor.AsyncExecutionAspectSupport#doSubmit
org.springframework.aop.interceptor.AsyncExecutionAspectSupport#handleError
org.springframework.scheduling.annotation.AbstractAsyncConfiguration#setConfigurers
org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor (클래스 선언부 - 상속 계층)
```

## 8. 런타임 관찰

[`AsyncMethodTest`](../../experiments/async-method-lab/src/test/java/lab/experiments/async/AsyncMethodTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| `TaskExecutor` 빈 없이 같은 메서드를 두 번 호출 | 매번 **다른** 스레드에서 실행됨(호출자 스레드와도 다름) — `SimpleAsyncTaskExecutor`가 호출마다 새 스레드를 만듦 |
| `void` 반환 메서드가 예외를 던짐 | 호출 자체는 예외 없이 즉시 반환됨. 비동기적으로 `AsyncUncaughtExceptionHandler`가 호출됨(메서드 이름·예외 타입 모두 정확히 전달) |
| `Future` 반환 메서드가 예외를 던짐 | `future.get()`이 `ExecutionException`을 던지고, 그 원인(`cause`)이 원래 예외 그대로 |
| self-invocation으로 `this.recordThreadName()` 호출 | 프록시를 거치지 않아 별도 스레드로 제출되지 않음 — 호출자와 정확히 같은 스레드에서 실행됨 |
| `@EnableAsync` 없는 컨테이너의 `@Async` 메서드 | 예외 없이, 별도 스레드도 없이 그냥 평범한 동기 호출로 실행됨 — 호출자와 같은 스레드 |

**직접 겪은 것**: 다섯 번째 행을 설계하면서 28번의 `UnenabledTask`(카운터가 0인 채로 남는 "완전한 무력화")를 그대로 따라 하려다가, `@Async`의 무력화는 성격이 다르다는 것을 깨달았다 - `@Scheduled`가 없는 인프라는 "아무도 호출하지 않는다"는 결과로 나타나지만, `@Async`가 없는 인프라는 "누군가(호출자 자신)가 이미 부르고 있던 메서드가 그냥 평범하게 실행된다"는 결과로 나타난다. 그래서 이 실험은 카운터가 아니라 "호출자 스레드와 같은가"를 검증하도록 다시 설계했다 - 같은 "선언적 애노테이션이 인프라 없이는 무력하다"는 주제라도, 그 무력화가 정확히 어떤 모양으로 드러나는지는 확장점의 성격(가로채는 것 vs 새로 호출하는 것)에 따라 달랐다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다. 이번 주제도 28번처럼 핵심 클래스 몇 개의 소스 자체가 가장 직접적인 근거였다.

- `AsyncExecutionAspectSupport#doSubmit`의 실제 소스: 반환 타입을 `CompletableFuture`/`ListenableFuture`/`Future`/`void`(또는 Kotlin `Unit`) 순서로 분기하고, 그 무엇도 아니면 `IllegalArgumentException("Invalid return type for async method (only Future and void supported)")`을 던진다는 것을 확인했다 - `UnenabledAsyncService`가 `String`을 반환하고도 아무 문제 없이 동작하는 이유(9번 절의 반례)를 정확히 설명해 준다: `@EnableAsync`가 없으면 이 검증 코드 자체가 실행되지 않는다.
- `AsyncExecutionAspectSupport#handleError`의 실제 소스: `Future.class.isAssignableFrom(method.getReturnType())`이면 `ReflectionUtils.rethrowException(ex)`로 원본 예외를 그대로 다시 던지고, 그 외의 경우에만 `AsyncUncaughtExceptionHandler`로 위임한다는 것을 확인했다 - Javadoc 원문도 "If the return type of the method is a Future object, the original exception can be propagated by just throwing it at the higher level"이라고 명시한다. 2·3번째 행의 직접적인 근거다.
- `AsyncExecutionInterceptor#getDefaultExecutor`의 실제 소스: 상위 클래스(`AsyncExecutionAspectSupport`)의 탐색이 `null`을 반환하면 `new SimpleAsyncTaskExecutor()`로 폴백한다는 것을 확인했다 - 28번의 `TaskSchedulerRouter`가 `Executors.newSingleThreadScheduledExecutor()`(스레드 **하나**)로 폴백하는 것과 정확히 대비되는 지점이다.
- `AbstractAsyncConfiguration#setConfigurers`의 실제 소스: `this.executor = adapt(configurer, AsyncConfigurer::getAsyncExecutor)`가 `SingletonSupplier` 기반이라, `AsyncConfigurer`가 `null`을 반환해도(이번 실험처럼 `getAsyncExecutor()`를 오버라이드하지 않으면) 인터셉터 자신의 기본 폴백 체인이 그대로 살아 있다는 것을 확인했다 - `AsyncConfigurer`로 예외 핸들러만 바꾸면서 실행자 폴백은 그대로 관찰할 수 있었던 이유다(4번 절 설계 근거).

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `AnnotationAsyncExecutionInterceptor`의 골격(프록시 + 애노테이션 → 전략 객체)은 `mini-spring/mini-aop`·`mini-transaction`이 이미 다뤘고, 25·26번이 그 골격의 반복이라는 것도 이미 확인했다 - 이번 주의 진짜 가치는 골격이 아니라 **반환 타입에 따라 갈라지는 예외 처리 경로**와 **`@Scheduled`와 정반대인 기본 실행자 정책**에 있었다.

## 11. Spring 설계 의도

- **왜 `void` 예외와 `Future` 예외가 다르게 처리되는가**: `Future`를 반환하는 메서드는 호출자가 명시적으로 `get()`을 불러야 결과(또는 실패)를 확인할 수 있다는 계약을 이미 갖고 있다 - 그 계약을 활용하면 예외를 있는 그대로 전달해도 안전하다. 반면 `void` 메서드는 애초에 "결과를 확인하겠다"는 의사 자체가 호출자에게 없다(fire-and-forget) - 예외를 어딘가로 다시 던져 봐야 그걸 받아 줄 스택 프레임이 없다(비동기 스레드 위에서 발생했으므로 호출자의 스택과 이미 분리돼 있다). `AsyncUncaughtExceptionHandler`라는 별도 SPI는 "호출자가 결과를 기다리지 않기로 한 이상, 실패를 알리는 것도 호출자가 아니라 애플리케이션 전체의 책임(로깅, 모니터링, 알림)"이라는 다른 계약으로 이 문제를 처리한다.
- **왜 `@Async`의 기본 실행자는 `@Scheduled`와 정반대(무제한 스레드 생성 vs 스레드 하나 공유)인가**: 둘 다 "사용자가 아직 명시적으로 설정하지 않았다"는 같은 상황에서 나온 기본값이지만, 실패 모드가 다르다. `@Scheduled`는 보통 소수의, 예측 가능한 반복 작업이라 스레드 하나를 공유해도(느려질 뿐) 시스템이 붕괴하지는 않는다 - 반면 `@Async`는 요청 트래픽처럼 호출 빈도가 예측 불가능할 수 있는 용도로도 쓰인다. 만약 `@Async`의 기본값도 스레드 하나였다면, 동시 요청이 많아지는 순간 사실상 모든 비동기 처리가 완전히 직렬화돼 버려서 원래 얻고자 했던 "비동기"라는 목적 자체를 잃는다 - 그래서 "일단 막지는 않는다"(무제한 스레드 생성)는 방향으로 기본값을 잡되, 그 대가(무제한 스레드로 인한 자원 고갈 위험)는 `SimpleAsyncTaskExecutor`라는 이름 자체("Simple" - 프로덕션 등급이 아니라는 신호)로 사용자에게 암묵적으로 경고한다.
- **왜 `AsyncConfigurer`는 두 메서드를 독립적으로 오버라이드할 수 있게 설계됐는가(9번 절)**: 실행자 정책과 예외 처리 정책은 서로 다른 관심사다 - 실행자만 바꾸고 싶은 사용자가 예외 핸들러까지 강제로 다시 구현해야 한다면(혹은 그 반대라면) 불필요한 결합이 생긴다. `default` 메서드로 각각 독립적인 "선택적 오버라이드"를 허용하고, 오버라이드하지 않은 쪽은 인터셉터 자신의 표준 폴백이 그대로 적용되게 한 것은, 이 저장소가 반복해서 봐 온 "필요한 것만 좁게 확장하게 한다"는 원칙이 콜백 인터페이스 설계 수준에서도 나타난 사례다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Scheduled`(28번)와 `@Async`가 "TaskExecutor 인프라가 없을 때"라는 같은 상황에서 정반대의 기본값(스레드 하나 공유 vs 무제한 스레드 생성)을 택했다는 것 - 28번을 먼저 보지 않았다면 이 대비 자체를 놓쳤을 것이다. 두 확장점을 나란히 실행해 본 뒤에야, "기본값이 왜 이렇게 다른가"라는 질문이 자연스럽게 따라왔다.
- 예상 밖이었던 것: `@Async`가 결국 프록시 기반이라는 것 - 28번 직후라서 "이것도 리플렉션 직접 호출일 것"이라 예상했는데, 오히려 26번(`@Validated`)과 같은 프록시 생성 계열이었다. 확장점의 형태는 "이 문제가 호출을 가로챌 필요가 있는가"에 달려 있지, 최근에 본 사례와 비슷할 거라는 직관에 달려 있지 않다는 걸 다시 확인했다.
- 예상대로였던 것(재확인): self-invocation 우회 - 11·12·25·26번에서 반복해서 본 원칙이 다섯 번째로 재확인됐다.
- 새로 배운 것: `@EnableAsync`가 없을 때의 "무력화"가 `@EnableScheduling`이 없을 때(28번)와 겉모습이 다르다는 것 - 하나는 "아무 일도 안 일어남"(카운터 0)이고 다른 하나는 "그냥 평범하게, 동기적으로 실행됨"(호출자 스레드와 동일)이다. 같은 "인프라 없이는 애노테이션이 무력하다"는 주제도, 그 확장점이 "새로 호출을 만드는 것"이냐 "기존 호출을 가로채는 것"이냐에 따라 정확히 어떤 모습으로 무력해지는지가 달라진다는 것을 이번에 구체적으로 비교했다.
