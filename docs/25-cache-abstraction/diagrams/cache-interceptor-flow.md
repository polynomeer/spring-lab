# 일반 경로 vs sync 경로, 그리고 self-invocation이 프록시 경계를 건너뛰는 지점

[`cache-abstraction.md`](../cache-abstraction.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 일반 `@Cacheable`의 "조회 후 계산 후 쓰기"가 세 단계로 분리되어 있어 원자적이지 않다는 것, 오른쪽은 `sync = true`가 `Cache#get(key, Callable)` 하나로 조회·계산·쓰기를 위임해 원자성을 얻는다는 것을 대비시킨다.

```mermaid
flowchart TD
    subgraph Normal["일반 @Cacheable 경로 (원자적이지 않음)"]
        A1["CacheInterceptor#invoke"] --> A2["findCachedValue()<br/>cache.get(key) - 읽기"]
        A2 -->|히트| A3["캐시된 값 반환"]
        A2 -->|미스| A4["invokeOperation()<br/>대상 메서드 실행"]
        A4 -->|정상 반환| A5["cache.put(key, value) - 쓰기<br/>(읽기와 별도 단계)"]
        A4 -->|예외| A6["ThrowableWrapper로 전파<br/>cache.put 호출 안 됨"]
    end

    subgraph Sync["sync = true 경로 (원자적)"]
        B1["CacheInterceptor#invoke"] --> B2["executeSynchronized()"]
        B2 --> B3["cache.get(key, Callable)<br/>ConcurrentMapCache → store.computeIfAbsent(key, ...)"]
        B3 -->|"같은 key로 동시 진입"| B4["매핑 함수는 딱 한 번만 실행<br/>나머지 스레드는 결과를 기다림"]
    end

    style A2 fill:#333,color:#fff
    style A5 fill:#333,color:#fff
    style B3 fill:#161,color:#fff
```

```mermaid
sequenceDiagram
    participant Caller as 호출자
    participant Proxy as CGLIB/JDK 프록시
    participant Interceptor as CacheInterceptor
    participant Cache as Cache (products)
    participant Target as ProductLookupServiceImpl

    Caller->>Proxy: findById("p1")
    Proxy->>Interceptor: invoke()
    Interceptor->>Cache: get("p1")
    Cache-->>Interceptor: 미스
    Interceptor->>Target: 실제 메서드 실행
    Target-->>Interceptor: Product 반환
    Interceptor->>Cache: put("p1", product)
    Interceptor-->>Caller: Product 반환

    Note over Target: refreshViaSelfInvocation() 내부에서
    Target->>Target: this.findById("p1")
    Note over Interceptor,Cache: 프록시를 거치지 않아 이 경로 전체가 생략됨 -<br/>읽기도 쓰기도 일어나지 않는다
    Target-->>Target: 재계산된 Product 반환 (캐시는 그대로 예전 값)
```
