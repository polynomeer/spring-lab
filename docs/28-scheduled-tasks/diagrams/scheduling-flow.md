# 프록시 없는 등록 경로, 그리고 단일 스레드 위에서 fixedRate와 fixedDelay가 갈라지는 지점

[`scheduled-tasks.md`](../scheduled-tasks.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. 위쪽은 `@Scheduled`가 25·26번의 인터셉터 경로와 구조적으로 다르다는 것(가로챌 호출이 없다), 아래쪽은 실행 시간이 주기보다 길 때 두 모드의 시작 시각 간격이 왜 벌어지는지를 대비시킨다.

```mermaid
flowchart TD
    subgraph AOP["25·26번: 프록시 기반 (호출을 가로챈다)"]
        A1["외부에서 메서드 호출"] --> A2["프록시가 가로챔"] --> A3["Interceptor 실행"] --> A4["대상 메서드 실행"]
    end

    subgraph Sched["이번 주: @Scheduled (호출을 새로 만든다)"]
        B1["빈 생성 완료"] --> B2["ScheduledAnnotationBeanPostProcessor가<br/>리플렉션으로 Runnable 생성"]
        B2 --> B3["TaskScheduler에 등록<br/>(scheduleAtFixedRate/scheduleWithFixedDelay)"]
        B3 --> B4["아무도 호출한 적 없는 이 메서드를<br/>스케줄러 스레드가 직접 호출"]
    end

    style A2 fill:#333,color:#fff
    style B2 fill:#161,color:#fff
```

```mermaid
gantt
    dateFormat X
    axisFormat %Lms
    title 단일 스레드 위에서의 시작 시각 (실행 250ms, 회색 = 실행 중)

    section fixedRate(50ms)
    실행 1 :active, r1, 0, 250
    실행 2 :active, r2, 250, 500
    실행 3 :active, r3, 500, 750

    section fixedDelay(250ms)
    실행 1 :active, d1, 0, 250
    대기(delay) :crit, dw1, 250, 500
    실행 2 :active, d2, 500, 750
    대기(delay) :crit, dw2, 750, 1000
    실행 3 :active, d3, 1000, 1250
```
