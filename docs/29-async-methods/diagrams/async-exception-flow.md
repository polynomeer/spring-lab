# 반환 타입에 따라 갈라지는 예외 경로, 그리고 28번과 정반대인 기본 실행자

[`async-methods.md`](../async-methods.md)의 6번(호출 흐름)·11번(설계 의도) 항목을 시각화한 것. 위쪽은 같은 예외가 반환 타입에 따라 완전히 다른 두 경로로 갈라지는 것, 아래쪽은 `@Scheduled`(28번)의 "스레드 하나 공유"와 `@Async`의 "호출마다 새 스레드"가 같은 상황("실행자 미설정")에서 정반대 기본값을 택했다는 것을 대비시킨다.

```mermaid
flowchart TD
    A["대상 메서드가 예외를 던짐<br/>(invocation.proceed()에서 catch)"] --> B{"반환 타입이<br/>Future 계열인가?"}
    B -->|"예 (Future/CompletableFuture)"| C["ReflectionUtils.rethrowException(ex)<br/>원본 예외를 그대로 다시 던짐"]
    C --> D["executor.submit(task)가 돌려준 Future 안에<br/>실패로 담김"]
    D --> E["호출자가 future.get() 호출<br/>→ ExecutionException(cause=원본 예외)"]

    B -->|"아니오 (void)"| F["exceptionHandler.handleUncaughtException(ex, method, args)"]
    F --> G["기본값: SimpleAsyncUncaughtExceptionHandler<br/>→ 로그만 남기고 끝"]
    G --> H["호출자는 이미 즉시 반환받은 뒤 -<br/>이 실패를 알 방법이 전혀 없음"]

    style C fill:#161,color:#fff
    style F fill:#333,color:#fff
    style H fill:#611,color:#fff
```

```mermaid
flowchart LR
    subgraph S28["28번: @Scheduled 기본 폴백"]
        S1["TaskScheduler 빈 없음"] --> S2["Executors.newSingleThreadScheduledExecutor()"]
        S2 --> S3["여러 @Scheduled 빈이<br/>스레드 하나를 공유<br/>(하나가 느리면 전부 밀림)"]
    end

    subgraph S29["이번 주: @Async 기본 폴백"]
        A1["TaskExecutor 빈 없음"] --> A2["new SimpleAsyncTaskExecutor()"]
        A2 --> A3["호출마다 새 스레드 생성<br/>(풀링도, 상한도 없음)"]
    end

    style S3 fill:#333,color:#fff
    style A3 fill:#611,color:#fff
```
