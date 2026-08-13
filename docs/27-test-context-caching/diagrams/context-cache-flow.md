# 캐시 히트/미스 경로, 그리고 @DirtiesContext의 "마킹 후 지연 제거"

[`test-context-caching.md`](../test-context-caching.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. 위쪽은 두 테스트 클래스가 같은 설정을 쓸 때 get-or-create가 어떻게 하나의 컨텍스트로 수렴하는지, 아래쪽은 `@DirtiesContext`가 표시된 클래스 자신은 여전히 캐시를 재사용하고, 그 클래스가 끝난 "뒤"에야 실제 제거가 일어난다는 시차를 보여준다.

```mermaid
sequenceDiagram
    participant A as SharedOnceProbeA
    participant B as SharedOnceProbeB
    participant Delegate as CacheAwareContextLoaderDelegate
    participant Cache as DefaultContextCache (JVM 전역, static)

    A->>Delegate: loadContext(mergedConfig)
    Delegate->>Cache: get(mergedConfig)
    Cache-->>Delegate: null (미스)
    Delegate->>Delegate: loadContextInternal() - 새 ApplicationContext 생성
    Delegate->>Cache: put(mergedConfig, context)
    Delegate-->>A: context (1번째 생성)

    B->>Delegate: loadContext(mergedConfig)
    Note over B,Delegate: SharedOnceProbeB는 별도의<br/>TestContextManager를 갖지만,<br/>mergedConfig는 완전히 동일하다
    Delegate->>Cache: get(mergedConfig)
    Cache-->>Delegate: context (히트!)
    Delegate-->>B: 같은 context 그대로 반환 - 생성 카운터 그대로
```

```mermaid
flowchart TD
    A["DirtiesScenarioProbeA 실행<br/>캐시 미스 → 새 컨텍스트 생성(1회)"] --> B["DirtiesScenarioProbeDirty 실행<br/>같은 설정 → 캐시 히트 → 재사용(그대로 1회)"]
    B --> C["클래스의 모든 @Test 종료"]
    C --> D["DirtiesContextTestExecutionListener#afterTestClass()<br/>markApplicationContextDirty() 호출"]
    D --> E["contextCache.remove(mergedConfig) + context.close()"]
    E --> F["DirtiesScenarioProbeB 실행<br/>같은 설정인데 이번엔 캐시 미스 → 새 컨텍스트 생성(2회)"]

    style B fill:#161,color:#fff
    style D fill:#333,color:#fff
    style F fill:#611,color:#fff
```
