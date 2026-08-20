# TaskDecorator — ThreadLocal이 절대 스스로 넘지 않는 경계를 메우는 지점

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`41`](../41-smart-initializing-singleton/smart-initializing-singleton.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`29-async-methods`](../29-async-methods/async-methods.md)는 `@Async`가 대상 메서드를 별도 스레드에서 실행한다는 것과 그 기본 실행자(`SimpleAsyncTaskExecutor`)의 위험성을 다뤘는데, 그 "별도 스레드"라는 사실 자체가 만드는 훨씬 실무적인 문제 - 호출자 스레드의 `ThreadLocal` 상태(요청 ID, 로깅 컨텍스트 등)가 실행 스레드에는 전혀 보이지 않는다는 것 - 는 다루지 않았다. `TaskDecorator`가 정확히 이 경계를 메우기 위한 확장점이고, 이번 문서에서 그 메커니즘과 함정(스레드 풀 재사용으로 인한 상태 누출)을 직접 확인한다.

## 1. 이번 질문

- `@Async` 메서드가 실행되는 스레드는 호출자의 `ThreadLocal` 값을 볼 수 있는가?
- `TaskDecorator`는 그 문제를 어떻게 해결하는가 - 정확히 언제, 어느 스레드 위에서 실행되는가?
- `TaskDecorator` 인스턴스 자체는 설정 시점에 한 번만 만들어지는데, 매 호출마다 다른 호출자의 컨텍스트를 정확히 캡처할 수 있는 이유는 무엇인가?
- 스레드 풀이 스레드를 재사용한다는 사실이 `TaskDecorator` 구현에 어떤 책임을 추가로 지우는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Task Execution and Scheduling")는 `TaskDecorator`를 "제출되는 모든 `Runnable`에 적용할 수 있는 콜백"이라고 소개하고, 대표적인 용도로 "작업 실행 주변에 실행 컨텍스트를 설정하는 것"(예: MDC, 보안 컨텍스트 전파)을 든다.
- `TaskDecorator` Javadoc은 "이 데코레이터가 반드시 사용자가 제출한 `Runnable`/`Callable`에 직접 적용되는 것은 아니고, 실제 실행 콜백(사용자 작업을 감싼 래퍼일 수 있음)에 적용된다"와 "`Future` 기반 작업의 경우 예외가 `run()`에서 전파되지 않는 래퍼일 수 있다"는 것을 명시한다 - 이 두 문장의 의미는 소스를 봐야 정확히 이해된다.
- 문서는 "언제(어느 스레드에서) `decorate()`가 호출되는지"는 명시하지 않는다 - 이번 실험의 핵심 질문이다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `decorate()`가 풀 스레드 위에서, 즉 작업이 실제로 실행되기 직전에 호출될 거라 예상했다 — **틀렸다.** `decorate()` 자체는 작업을 **제출하는** 스레드(호출자 스레드) 위에서 즉시 호출된다 - `ThreadPoolTaskExecutor`의 `execute()`가 실제로 풀에 넘기기 **전에** 이 호출을 먼저 한다. 풀 스레드에서 실행되는 것은 `decorate()`가 반환한 새 `Runnable`의 `run()`뿐이다.
- 이 순서 때문에, `decorate()` 안에서 `ThreadLocal.get()`을 호출하면 호출자의 값을 정확히 캡처할 수 있다 - 그 값을 반환된 `Runnable`의 클로저에 담아 뒀다가, 나중에 풀 스레드 위에서 `run()`이 실행될 때 그 캡처된 값을 그 스레드에 다시 심어 주는 방식이다.
- `TaskDecorator`를 그냥 "값을 심어 주기만" 하면 충분할 거라 예상했다 — **틀렸다.** 스레드 풀은 스레드를 재사용하므로, 작업이 끝난 뒤 값을 정리(또는 원래 상태로 복원)하지 않으면 그 값이 같은 스레드에서 나중에 실행될 **완전히 무관한** 다음 작업으로 새어 나간다 - 직접 재현해서 확인했다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/task-decorator-lab`](../../experiments/task-decorator-lab)

```java
public class RequestContextPropagatingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        String captured = RequestContext.get();      // 호출자 스레드에서 즉시 캡처
        return () -> {
            String previous = RequestContext.get();   // 풀 스레드가 이전에 갖고 있던 값
            RequestContext.set(captured);
            try {
                runnable.run();                        // 여기서부터 풀 스레드에서 실행
            } finally {
                if (previous != null) RequestContext.set(previous);
                else RequestContext.clear();            // 정리하지 않으면 다음 작업으로 누출
            }
        };
    }
}
```

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new RequestContextPropagatingTaskDecorator());
    return executor;
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `TaskDecorator` | `Runnable decorate(Runnable)` 하나짜리 콜백 - 실제 작업을 감싼 새 `Runnable`을 돌려줌 |
| `ThreadPoolTaskExecutor` | `setTaskDecorator()`로 등록받고, 내부 `execute()`에서 실제로 적용 |
| (29번에서 다룬) `SimpleAsyncTaskExecutor` | `TaskDecorator`를 지원하는 또 다른 실행자 - `@Async` 기본 폴백이지만 `TaskDecorator` 자체는 어느 실행자를 쓰든 같은 방식으로 동작 |
| (실험) `RequestContext` | 호출자와 실행 스레드 사이의 격차를 보여주는 `ThreadLocal` 보관소 |
| (실험) `RequestContextPropagatingTaskDecorator` | 캡처 → 심기 → (재사용 대비) 정리까지 전부 책임지는 구현체 |

## 6. 호출 흐름

```text
[호출자 스레드]
service.readRequestContext()                       (@Async 메서드 호출)
  → AnnotationAsyncExecutionInterceptor#invoke       (29번에서 다룬 프록시 인터셉터)
      → executor.submit(callable) 또는 executor.execute(task)
          → ThreadPoolTaskExecutor 내부 execute(Runnable command)
              → decorated = taskDecorator.decorate(command)   ← 아직 호출자 스레드!
                  → captured = RequestContext.get()            ← 호출자의 값을 여기서 캡처
                  → return 새 Runnable (캡처값을 클로저로 들고 있음)
              → super.execute(decorated)                       ← 이제야 풀에 실제로 제출

[풀 스레드 - 나중에, 어쩌면 재사용된 스레드]
decorated.run() 실행
  → previous = RequestContext.get()      (이 스레드가 이전 작업에서 남긴 값일 수도 있음)
  → RequestContext.set(captured)          (호출자의 값을 이 스레드에 심음)
  → try { command.run() }                 (원래 @Async 작업 본문 실행 - 이제 RequestContext.get()이
                                             호출자의 값을 정확히 봄)
  → finally { 이전 값 복원 또는 clear() }   (다음 작업을 위한 정리 - 없으면 누출)
```

`decorate()`가 호출자 스레드에서 실행되는 지점과, 풀 스레드 재사용으로 인한 누출 위험을 함께 그린 다이어그램: [`diagrams/task-decorator-propagation.md`](diagrams/task-decorator-propagation.md)

## 7. 브레이크포인트

25~41번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어느 스레드에서 어떤 값이 보이는가"라는 결과였고, `RequestContext.get()`을 직접 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.core.task.TaskDecorator (인터페이스 Javadoc)
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor#setTaskDecorator
org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor (내부 execute() 오버라이드 - taskDecorator.decorate() 호출 지점)
```

## 8. 런타임 관찰

[`TaskDecoratorTest`](../../experiments/task-decorator-lab/src/test/java/lab/experiments/taskdecorator/TaskDecoratorTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `TaskDecorator` 없이 호출자가 `RequestContext`를 설정하고 `@Async` 호출 | 실행 스레드에서 `null` - 전혀 전파 안 됨 |
| `TaskDecorator`를 설정한 실행자로 같은 시나리오 | 호출자가 설정한 값이 정확히 전달됨 |
| 같은 서비스를 서로 다른 컨텍스트 값으로 두 번 연속 호출 | 매번 그 순간 호출자의 값을 정확히 캡처(첫 번째 값이 두 번째 호출에 남아있지 않음) |
| 풀 크기 1로 스레드 재사용을 강제한 뒤, 컨텍스트 있는 호출 → 없는 호출 순서로 실행 | 두 번째 호출은 `null` - 첫 번째 작업이 남긴 값이 새어 나오지 않음(`finally`의 정리 덕분) |

**직접 겪은 것**: 네 번째 실험(누출 방지)을 검증하려면 "정말로 같은 스레드가 재사용되는가"를 확신할 수 있어야 했다 - 풀 크기를 기본값(여러 스레드)으로 뒀다면, 두 작업이 서로 다른 스레드에서 실행돼서 애초에 누출이 일어날 조건 자체가 안 만들어졌을 수도 있다. `corePoolSize`/`maxPoolSize`를 둘 다 1로 고정해서 스레드 재사용을 결정론적으로 강제하고 나서야, `finally` 블록의 정리 로직이 실제로 무언가를 막고 있다는 것을 확신 있게 증명할 수 있었다 - 그냥 "복원 코드를 넣었다"는 것과 "그 코드가 실제로 막아 주는 문제를 재현해서 확인했다"는 것은 다르다는 걸 다시 느꼈다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `ThreadPoolTaskExecutor`의 실제 소스: 내부적으로 만드는 `ExecutorService` 래퍼의 `execute(Runnable command)`가 `Runnable decorated = command; if (taskDecorator != null) { decorated = taskDecorator.decorate(command); ... } super.execute(decorated);` 순서로 짜여 있다는 것을 확인했다 - `taskDecorator.decorate()` 호출이 `super.execute()`(실제로 풀에 작업을 넘기는 지점)보다 **코드상 먼저**라는 것이, "`decorate()`는 제출 스레드에서 실행된다"는 6번 절 전체의 근거다.
- `TaskDecorator` 인터페이스의 Javadoc 원문 "such a decorator is not necessarily being applied to the user-supplied Runnable/Callable but rather to the actual execution callback (which may be a wrapper around the user-supplied task)"을 확인했다 - `@Async`의 경우 `decorate()`에 전달되는 `command`가 사용자가 작성한 `@Async` 메서드 본문 그 자체가 아니라, `AnnotationAsyncExecutionInterceptor`가 이미 한 번 감싼 `Callable` 기반 작업이라는 뜻이다. `TaskDecorator`는 그 감싸진 형태를 신경 쓸 필요 없이, "무엇이 됐든 결국 실행될 `Runnable`"만 다시 한번 감싸면 된다.
- `setTaskDecorator()`의 Javadoc이 "Exception handling in TaskDecorator implementations may be limited... the exposed Runnable will be a wrapper which does not propagate any exceptions from its run method"라고 경고하는 것도 확인했다 - `Future` 기반 작업(`@Async`가 `CompletableFuture`를 반환하는 경우 등)에서는 예외가 이 `run()`을 통해서가 아니라 별도 경로로 `Future`에 담기므로, `TaskDecorator` 안에서 예외를 관찰/처리하려는 시도는 기대한 대로 동작하지 않을 수 있다는 뜻이다 - 이번 실험은 정상 흐름만 다뤘고 이 경고 상황까지는 재현하지 않았다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `TaskDecorator`는 인터페이스 하나(`Runnable decorate(Runnable)`)라 골격을 재구현할 이유가 없다 - 이번 주의 가치는 그 골격이 아니라, "`decorate()`가 어느 스레드에서 실행되는가"라는 타이밍과 "스레드 재사용이 강제하는 정리 책임"이라는 구체적인 함정에 있었다 - 28번(`@Scheduled`)·29번(`@Async`)이 같은 이유로 mini 구현을 생략했던 것과 같은 판단이다.

## 11. Spring 설계 의도

- **왜 `decorate()`를 제출 스레드에서, 풀 스레드가 아니라 미리 호출하는가**: 만약 `decorate()`가 풀 스레드 위에서 호출됐다면, 그 시점엔 이미 호출자의 스레드가 사라졌거나 다른 일을 하고 있을 수 있다 - 호출자의 `ThreadLocal` 상태를 캡처하려면, 그 상태가 아직 살아 있는 **호출자 스레드가 실행되는 바로 그 순간**에 캡처해야 한다. 작업을 제출하는 시점(아직 호출자 스레드 위)에 `decorate()`를 실행하는 것은, "전파하고 싶은 컨텍스트는 반드시 원본이 아직 유효할 때 붙잡아야 한다"는 이 문제의 본질적인 제약을 그대로 반영한 설계다.
- **왜 정리(복원/clear) 책임을 프레임워크가 대신 해 주지 않고 구현체에 완전히 맡기는가**: 36번(커스텀 `Scope`)에서 이미 비슷한 패턴을 봤다 - 컨테이너는 "무엇을 전파해야 하는지"(어떤 `ThreadLocal`, 어떤 값)를 전혀 모른다. `TaskDecorator`가 감싸는 대상이 정확히 무엇을 전파하고 싶어 하는지는 애플리케이션 코드만 아는 정보이므로, "심고 나서 다시 치운다"는 대칭적인 책임 전체를 구현체 하나에 완전히 위임하는 것이 유일하게 일반적인 설계다 - 프레임워크가 "아마 이런 걸 정리해야겠지"라고 짐작해서 자동으로 처리해 주려 하면, 반쯤만 맞고 반쯤은 틀린 위험한 자동화가 된다.
- **왜 `TaskDecorator`가 `Runnable` 하나만 받고 반환하는 아주 좁은 인터페이스인가**: 이 확장점이 하는 일은 정확히 "작업 실행 전후에 뭔가를 한다"는 것 하나뿐이다 - 25~41번 내내 반복해서 봐 온 "필요한 만큼만 좁게 확장한다"는 원칙이, 이번엔 "실행 컨텍스트 전파"라는 문제에 가장 얇은 확장점(함수형 인터페이스 하나) 하나로 응답한 사례다. MDC 전파, 보안 컨텍스트 전파, 요청 ID 전파처럼 서로 완전히 다른 용도라도, 전부 "`Runnable`을 감싼다"는 같은 모양으로 표현할 수 있기 때문에 이 좁은 인터페이스 하나로 충분하다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `decorate()`가 풀 스레드가 아니라 호출자 스레드에서 실행된다는 것 - "데코레이터가 작업을 감싼다"는 이름만 보고는 그게 언제 실행되는지 짐작하기 어려웠는데, 그 타이밍 하나가 이 메커니즘이 성립하는 이유 전부였다.
- 예상 밖이었던 것: 스레드 재사용으로 인한 상태 누출이 이론적인 걱정이 아니라 실제로 재현 가능한 문제였다는 것 - 풀 크기를 1로 고정하고 나서, `finally` 없이 값만 심었다면 어떻게 됐을지가 훨씬 구체적으로 느껴졌다(실제로 그렇게 짜 보고 실패를 재현하지는 않았지만, 어떤 조건에서 그 실패가 재현되는지는 명확히 확인했다).
- 예상대로였던 것(재확인): `ThreadLocal`이 스레드 경계를 절대 스스로 넘지 않는다는 것 - 이건 Spring 고유의 동작이 아니라 자바 언어 자체의 `ThreadLocal` 계약이다. `TaskDecorator`는 그 계약을 바꾸는 게 아니라, 그 계약이 만드는 구멍을 애플리케이션 코드가 메울 수 있게 해 주는 통로일 뿐이라는 걸 재확인했다.
- 새로 배운 것: 이 시리즈에서 반복해서 봐 온 "컨테이너는 무엇을 전파/정리해야 하는지 모르므로 그 책임을 통째로 넘긴다"는 패턴(36번의 `Scope.registerDestructionCallback`과 정확히 같은 정신)이, 스레드 풀이라는 완전히 다른 층위에서도 똑같이 나타난다는 것 - "책임을 어디까지 프레임워크가 지고, 어디부터 사용자 코드가 지는가"라는 질문의 답이, 표면적으로 무관해 보이는 여러 확장점에서 계속 같은 원칙으로 수렴하고 있었다.
