# @Scheduled — 프록시도, 컨텍스트 캐시도 아닌 네 번째 확장점

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25-cache-abstraction`](../25-cache-abstraction/cache-abstraction.md)·[`26-method-validation`](../26-method-validation/method-validation.md)·[`27-test-context-caching`](../27-test-context-caching/test-context-caching.md)과 마찬가지로 그 카탈로그 밖의 심화 주제다. 앞의 셋(AOP 인터셉터 두 번, 테스트 컨텍스트 캐시 한 번)과 또 다르게, `@Scheduled`는 **프록시가 전혀 없다** - `ScheduledAnnotationBeanPostProcessor`는 빈을 감싸는 대신, 빈 생성이 끝난 뒤 리플렉션으로 직접 `Runnable`을 만들어 `TaskScheduler`에 등록해 버린다. self-invocation이라는 개념 자체가 성립하지 않는 확장점에서, 대신 어떤 문제(스레드 하나 공유, 예외로 인한 스케줄 중단)가 새로 생기는지를 확인한다.

## 1. 이번 질문

- `@Scheduled` 메서드는 프록시를 거치는가, 아니면 원본 빈을 직접 호출하는가?
- `fixedRate`와 `fixedDelay`는 실행 시간이 주기보다 길 때 실제로 어떻게 다르게 동작하는가?
- `TaskScheduler` 빈을 따로 등록하지 않으면 어떤 스케줄러가 쓰이는가 - 스레드는 몇 개인가?
- `@Scheduled` 메서드가 예외를 던지면 그 뒤의 실행은 계속되는가, 멈추는가?
- `@EnableScheduling`이 없으면 `@Scheduled`는 어떻게 되는가 — 22·26번 문서가 찾은 "조용한 무력화"가 여기서도 반복되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Task Execution and Scheduling", "Annotation Support for Scheduling and Async Execution")는 `@EnableScheduling` + `@Scheduled`로 주기적 작업을 등록할 수 있다고 설명하고, `fixedRate`/`fixedDelay`/`cron`/`initialDelay` 속성을 나열한다.
- 문서는 "`TaskScheduler` 빈이 없으면 기본 구현체로 폴백한다"고만 언급하고, 그 기본 구현체의 스레드 개수는 명시하지 않는다 — 이번 실험은 소스로 직접 확인했다.
- 예외 처리에 대해서는 레퍼런스 문서가 거의 다루지 않는다 — `ErrorHandler` SPI의 존재만 API 문서에 나온다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Scheduled`도 결국 애노테이션 기반 확장이니, 25·26번처럼 프록시 뒤에서 가로챌 거라 예상했다 — **틀렸다.** `ScheduledAnnotationBeanPostProcessor`는 프록시를 만들지 않는다 - 빈 인스턴스(다른 후처리기가 이미 프록시로 바꿔 놓았을 수도 있는 바로 그 참조)를 그대로 들고 리플렉션으로 `Runnable`을 만든다.
- 실행 시간이 주기보다 길면 `fixedRate`도 다음 실행을 정상적인 간격만큼 기다릴 거라 예상했다 — **틀렸다.** 예정 시각이 이미 지난 채로 실행이 끝나면, 단일 스레드는 추가로 기다리지 않고 즉시 다음 실행을 이어간다 - 간격이 사실상 실행 시간에 수렴해 버린다.
- `TaskScheduler`가 없으면 요청이 들어올 때마다 스레드를 새로 만드는 캐시드 스레드풀로 폴백할 거라 예상했다 — **틀렸다.** 단 하나의 스레드만 있는 `ScheduledExecutorService`로 떨어진다 - 서로 무관한 두 개의 `@Scheduled` 빈이 있어도 그 하나의 스레드를 공유해야 한다.
- `@Scheduled` 메서드가 예외를 던지면 (자바 표준 라이브러리의 `ScheduledExecutorService#scheduleAtFixedRate`처럼) 이후 실행이 전부 조용히 취소될 거라 예상했다 — **틀렸다.** Spring은 예외를 로그로 남기고 삼킨 뒤 다음 실행을 그대로 진행한다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/scheduled-task-lab`](../../experiments/scheduled-task-lab)

```java
@Configuration
@EnableScheduling
public class FixedRateOnlyConfig {
    @Bean
    public FixedRateTask fixedRateTask() {
        return new FixedRateTask();
    }
}

public class FixedRateTask {
    @Scheduled(fixedRate = 50)     // period(50ms) << 실행 시간(250ms)
    public void run() {
        startTimestampsNanos.add(System.nanoTime());
        Thread.sleep(250);
    }
}
```

```java
public class FlakyTask {
    @Scheduled(fixedRate = 50)
    public void run() {
        invocationCount.incrementAndGet();
        throw new IllegalStateException("항상 실패");   // 매번 예외
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ScheduledAnnotationBeanPostProcessor` | `@Scheduled` 메서드를 찾아 `TaskScheduler`에 등록하는 `BeanPostProcessor` - `MergedBeanDefinitionPostProcessor`이자 `Ordered`(`LOWEST_PRECEDENCE`)이기도 하다. 프록시를 만들지 **않는다** |
| `ScheduledMethodRunnable` | 실제로 `method.invoke(target, ...)`를 수행하는 `Runnable` - `TaskUtils.decorateTaskWithErrorHandler`로 감싸져서 예외를 잡는다 |
| `TaskUtils.LoggingErrorHandler` | 반복 작업(`isRepeatingTask=true`)의 기본 `ErrorHandler` - 예외를 로그로 남기고 **삼킨다**(재던지지 않음) |
| `TaskSchedulerRouter` | `TaskScheduler`/`ScheduledExecutorService` 빈을 찾고, 없으면 `Executors.newSingleThreadScheduledExecutor()`로 로컬 폴백을 만드는 라우터 |
| `ScheduledTaskRegistrar` | `fixedRate`/`fixedDelay`/`cron` 각각을 `TaskScheduler`의 대응 메서드(`scheduleAtFixedRate`/`scheduleWithFixedDelay`/`schedule`)로 변환해서 등록 |
| (실험) `FixedRateTask`/`FixedDelayTask` | 시작 시각을 직접 기록해서 두 모드의 실제 간격을 실측 |
| (실험) `SharedThreadTask` | 첫 실행이 어느 스레드에서 일어났는지 기록해서 기본 스케줄러의 스레드 개수를 실측 |

## 6. 호출 흐름

```text
빈 생성 완료 (initializeBean의 postProcessAfterInitialization 체인)
  → ScheduledAnnotationBeanPostProcessor#postProcessAfterInitialization(bean, beanName)
      (getOrder() == LOWEST_PRECEDENCE이므로, 이 체인에서 대체로 마지막에 실행된다 -
       즉 다른 후처리기가 이미 프록시로 바꿔 놓은 참조를 받을 수도 있다)
      → targetClass = AopProxyUtils.ultimateTargetClass(bean)
      → @Scheduled가 붙은 메서드를 리플렉션으로 스캔
      → processScheduledSync(scheduled, method, bean)
          → task = createRunnable(bean, method, ...)
              → ScheduledMethodRunnable(bean, method)를
                TaskUtils.decorateTaskWithErrorHandler(task, null, isRepeatingTask=true)로 감쌈
                  → 기본 ErrorHandler = LOG_AND_SUPPRESS_ERROR_HANDLER (반복 작업이므로)
          → processScheduledTask(scheduled, task, method, bean)
              → registrar.scheduleFixedRateTask(...) / scheduleFixedDelayTask(...) / scheduleCronTask(...)
                  → TaskScheduler(기본은 TaskSchedulerRouter → 로컬 단일 스레드 ScheduledExecutorService)
                      .scheduleAtFixedRate(task, ...) / .scheduleWithFixedDelay(task, ...)

매 실행마다:
  ScheduledExecutorService worker thread
    → DelegatingErrorHandlingRunnable#run()
        → try { targetTask.run() }         (= ScheduledMethodRunnable → method.invoke(bean, ...))
          catch (Throwable t) { errorHandler.handleError(t) }   ← 로그만 남기고 삼킴 - 재던지지 않음
```

프록시 기반 셋(25·26번)과 이번 주의 리플렉션 직접 호출 경로, 그리고 단일 스레드 위에서 fixedRate/fixedDelay가 실제로 어떻게 갈라지는지를 함께 그린 다이어그램: [`diagrams/scheduling-flow.md`](diagrams/scheduling-flow.md)

## 7. 브레이크포인트

25~27번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 — 핵심이 타이밍과 스레드 동일성이라, 실행 기반 관찰(시작 시각 목록, 스레드 이름)이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor#postProcessAfterInitialization
org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor#getOrder
org.springframework.scheduling.support.TaskUtils#decorateTaskWithErrorHandler
org.springframework.scheduling.support.TaskUtils#getDefaultErrorHandler
org.springframework.scheduling.config.TaskSchedulerRouter#determineDefaultScheduler
```

## 8. 런타임 관찰

[`ScheduledTaskTest`](../../experiments/scheduled-task-lab/src/test/java/lab/experiments/scheduled/ScheduledTaskTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `fixedRate = 50`, 실행 시간 250ms | 시작 시각 평균 간격 ≈ 실행 시간(250ms 근처) — period(50ms)는 이미 지나 있어서 추가로 기다리지 않음 |
| `fixedDelay = 250`, 실행 시간 250ms | 시작 시각 평균 간격 ≈ 실행 시간 + delay(500ms 근처) |
| `TaskScheduler` 빈 없이 두 개의 `@Scheduled` 빈 등록 | 둘 다 **정확히 같은 스레드 이름**에서 첫 실행 — 기본 스케줄러가 단일 스레드임을 직접 확인 |
| 매번 예외를 던지는 `@Scheduled` 메서드 | 2초 안에 3회 이상 계속 실행됨 — 예외가 이후 실행을 막지 않음 |
| `@EnableScheduling` 없는 컨테이너의 `@Scheduled` 메서드 | 300ms를 기다려도 **0회** 호출 — 애초에 스캔하는 주체가 없음 |

**직접 겪은 것**: 처음엔 `FixedRateTask`와 `FixedDelayTask`를 같은 `@Configuration`(따라서 같은 기본 단일 스레드) 안에 두고 나란히 측정하려 했는데, `fixedDelayTask`가 4초 타임아웃 안에 샘플 3개를 못 모아서 테스트가 실패했다. 원인은 이미 세 번째 행에서 확인한 바로 그것이었다 - 기본 스케줄러가 스레드 하나뿐이라서, `fixedRateTask`(250ms짜리 실행을 거의 쉬지 않고 반복)와 `fixedDelayTask`가 그 한 스레드를 서로 빼앗아 가며 실행됐고, 그래서 어느 쪽도 자기 자신의 스케줄링 규칙만으로는 설명되지 않는 타이밍이 나왔다. 세 번째 실험(스레드 공유)에서 확인한 사실이 첫 번째 실험(fixedRate vs fixedDelay)의 실패 원인이었다는 것을, 실행이 먼저 알려주고 나서야 두 실험을 완전히 별도의 컨텍스트로 분리했다 - 이 저장소가 반복해서 겪어 온 "실행이 예상을 깨고, 그 이유가 다른 실험에서 이미 확인한 메커니즘이었다"는 패턴이 이번에도 그대로 반복됐다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다. 이번 주제는 공식 유닛 테스트보다 **핵심 클래스 세 개의 소스 자체**가 더 직접적인 근거였다(11번 절 참고 - 목적이 명확한 몇 줄짜리 코드라 테스트보다 소스가 더 빨리 읽힌다).

- `ScheduledAnnotationBeanPostProcessor#getOrder()`가 `LOWEST_PRECEDENCE`를 반환한다는 것을 확인했다 - `Ordered` 빈 그룹 안에서 가장 늦게 실행된다는 뜻이다. 이론적으로는 `@Transactional`/`@Cacheable`의 자동 프록시 생성기가 먼저 실행돼 빈을 프록시로 바꿔치기한 뒤, 이 후처리기가 그 프록시를 넘겨받을 가능성이 있다는 뜻이다 - 다만 이번 실험은 프록시가 전혀 없는 순수 `@Scheduled` 빈만 다뤄서, 이 조합 자체(예: `@Transactional` + `@Scheduled`가 같은 메서드에 있을 때 스케줄된 호출도 트랜잭션을 타는지)는 직접 재현하지 않았다 - 소스로 확인한 사실이라는 것을 정직하게 밝혀 두고, 다음으로 이어질 수 있는 질문(12번 절)으로 남긴다.
- `TaskUtils`의 실제 소스: `getDefaultErrorHandler(boolean isRepeatingTask)`가 반복 작업이면 `LOG_AND_SUPPRESS_ERROR_HANDLER`, 1회성 작업이면 `LOG_AND_PROPAGATE_ERROR_HANDLER`를 돌려준다는 것, 그리고 `@Scheduled`의 `fixedRate`/`fixedDelay`/`cron`은 전부 `isRepeatingTask=true`로 등록된다는 것을 확인했다 — 4번째 행("예외가 계속돼도 실행이 안 멈춤")의 직접적인 근거다. `LoggingErrorHandler`의 Javadoc 자체가 "This will suppress the error so that subsequent executions of the task will not be prevented"라고 명시하고 있다.
- `TaskSchedulerRouter#determineDefaultScheduler`(실제로는 그 하위 로컬 폴백 경로)의 실제 소스: `Executors.newSingleThreadScheduledExecutor()`를 직접 호출한다는 것을 확인했다 - 세 번째 행의 근거이자, "직접 겪은 것"(8번 절)의 원인을 소스로 재확인한 지점이다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `ScheduledAnnotationBeanPostProcessor`의 골격(애노테이션을 스캔해서 등록한다) 자체는 `mini-spring/mini-component-scan`이 이미 다룬 "애노테이션 스캔 → 메타데이터 추출" 패턴과 크게 다르지 않고, 이번 주의 진짜 가치는 골격이 아니라 **타이밍**(fixedRate vs fixedDelay가 실행 시간과 상호작용하는 방식, 단일 스레드 공유, 예외 처리)에 있었다 - 17주차(SpringApplication)·25~27번이 같은 이유로 mini 구현을 생략했던 것과 같은 판단이다.

## 11. Spring 설계 의도

- **왜 `@Scheduled`는 프록시가 아니라 리플렉션 직접 호출을 선택했는가**: `@Transactional`/`@Cacheable`/`@Validated`는 모두 "메서드 호출을 가로채서 그 앞뒤에 뭔가를 한다"는 것이 본질이라 프록시가 자연스러운 선택이다. 반면 `@Scheduled`는 애초에 **누가 호출하는 메서드를 가로채는 게 아니라, 아무도 호출하지 않던 메서드를 새로 호출하는 주체를 만드는 것**이다 - 가로챌 "원래 호출"이 없으므로 인터셉터 체인이라는 도구 자체가 맞지 않는다. 대신 필요한 것은 "이 메서드를 어떻게 반복 실행할 스케줄로 등록하는가"이고, 그건 `TaskScheduler`라는 완전히 다른 확장점의 몫이다 - 문제의 성격이 다르면 그걸 푸는 확장점의 형태도 달라야 한다는 것을, 이 저장소가 25~27번에서 봐 온 인터셉터/캐시 패턴과 정면으로 대비되는 사례로 확인했다.
- **왜 반복 작업의 기본 예외 처리는 "삼킨다"인가**: 자바 표준 `ScheduledExecutorService#scheduleAtFixedRate`의 계약(예외가 나면 이후 실행 전체가 조용히 취소됨)을 그대로 물려받으면, 운영 환경에서 한 번의 일시적 오류(네트워크 타임아웃 등)가 그 스케줄된 작업 전체를 영구히 멈춰 버리는 심각한 결과로 이어진다 - 그리고 그 취소는 예외조차 던지지 않으므로 로그도 안 남고 그냥 조용히 멈춘다. Spring은 이 위험한 기본값을 감당할 수 없다고 판단하고, 자기 작업(`ScheduledMethodRunnable`)을 항상 `try-catch`로 감싸서 예외를 스케줄러에 전달하지 않는 쪽을 기본값으로 정했다 - "실패해도 다음 기회에 다시 시도한다"는 것이 반복 작업의 자연스러운 기대치라는 판단이다.
- **왜 기본 `TaskScheduler`는 스레드 풀이 아니라 스레드 하나인가**: 명시적으로 `TaskScheduler` 빈을 등록하지 않았다는 것은, 사용자가 동시성 요구사항을 아직 고려하지 않았다는 뜻으로 해석할 수 있다 - 이 상태에서 스레드 풀 크기를 임의로(예: CPU 코어 수만큼) 정해 버리면, 그 선택 자체가 또 다른 형태의 암묵적 설정이 된다. 대신 Spring은 "가장 단순하고 예측 가능한" 단일 스레드로 폴백해서, 여러 작업이 서로를 지연시킬 수 있다는 사실을 사용자가 (이번 실험처럼) 직접 겪고 나서야 명시적으로 `ThreadPoolTaskScheduler` 빈을 등록하도록 유도한다 - "조용히 최적화하는 대신, 조용히 최소한만 제공하고 필요하면 사용자가 직접 확장하게 한다"는 선택이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Scheduled`가 프록시 기반이 아니라는 것 - 25·26번을 거치며 "Spring의 선언적 기능은 다 프록시로 구현될 것"이라는 가정이 이번에 완전히 깨졌다. 가로챌 원래 호출이 없는 문제에는 인터셉터라는 도구 자체가 맞지 않는다는 걸 새로 확인했다.
- 예상 밖이었던 것: 기본 `TaskScheduler`가 스레드 풀이 아니라 스레드 **하나**라는 것 - 그리고 이 사실을 몰랐다면(이번 실험처럼 서로 다른 두 작업을 같은 컨텍스트에 둔 채) 각 작업의 스케줄링 규칙을 아무리 정확히 알아도 실제 타이밍을 설명할 수 없다는 것을, 직접 실패해 보고서야 체감했다(8번 절).
- 예상대로였던 것(재확인): `@EnableScheduling`이 없으면 `@Scheduled`가 그냥 무력해진다는 것 - 22번(`OptionalValidatorFactoryBean`)·26번(`@Validated` 없는 클래스)에 이어 세 번째로 확인한 "선언적 애노테이션은 그걸 읽어 줄 인프라가 등록돼 있어야만 의미가 있다"는 이 저장소의 반복된 패턴이다.
- 새로 배운 것: Spring이 자바 표준 라이브러리의 위험한 기본 동작(예외 시 스케줄 영구 취소)을 조용히 감싸서 더 안전한 기본값으로 바꿔치기하는 경우가 있다는 것 - `@Cacheable`(25번)이나 `@Validated`(26번)에서는 Spring이 표준 SPI(JSR-380 `Validator`, `Cache` 인터페이스)를 있는 그대로 위임했던 것과 달리, 여기서는 표준 라이브러리(`ScheduledExecutorService`) 위에 Spring이 직접 안전장치(`ErrorHandler`)를 얹었다 - "표준을 그대로 노출한다"와 "표준의 위험한 기본값을 감싼다" 중 어느 쪽을 택할지는 그 표준의 기본값이 얼마나 위험한가에 달려 있는 것으로 보인다.
- 다음으로 이어질 수 있는 질문: `ScheduledAnnotationBeanPostProcessor`가 `LOWEST_PRECEDENCE`라서 다른 자동 프록시 생성기보다 늦게 실행된다면, `@Transactional`과 `@Scheduled`를 같은 메서드에 함께 붙였을 때 스케줄된 호출도 실제로 트랜잭션 어드바이스를 타는지 - 이번 문서는 소스 근거(9번 절)만 확인했고 직접 재현하지는 않았다.
