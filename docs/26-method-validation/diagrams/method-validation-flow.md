# 프록시 생성 경로 비교, 그리고 파라미터/반환값 검증의 시점 차이

[`method-validation.md`](../method-validation.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. 위쪽은 `@Validated` 클래스가 프록시가 되는 경로가 12주차의 `AnnotationAwareAspectJAutoProxyCreator`와 왜 다른지, 아래쪽은 검증 실패가 "대상 메서드 실행 전"과 "실행 후" 중 어디서 갈리는지를 대비시킨다.

```mermaid
flowchart TD
    subgraph Old["12주차: @Transactional/@Cacheable 프록시 생성 (AbstractAutoProxyCreator)"]
        O1["빈 생성 완료"] --> O2["postProcessAfterInitialization()"]
        O2 --> O3["BeanFactoryAdvisorRetrievalHelper로<br/>빈 팩토리 전체에서 Advisor 빈들을 매번 수집"]
        O3 --> O4["수집된 Advisor들을 대상 빈에<br/>하나씩 매칭 - 여러 개가 붙을 수 있음"]
    end

    subgraph New["이번 주: @Validated 프록시 생성 (AbstractAdvisingBeanPostProcessor)"]
        N1["빈 생성 완료"] --> N2["postProcessAfterInitialization()"]
        N2 --> N3["isEligible() - AnnotationMatchingPointcut으로<br/>@Validated 클래스인지만 확인"]
        N3 -->|"true"| N4["afterPropertiesSet()에서 미리 만들어 둔<br/>단 하나의 고정 Advisor를 그대로 addAdvisor()"]
    end

    style O3 fill:#333,color:#fff
    style N4 fill:#161,color:#fff
```

```mermaid
sequenceDiagram
    participant Caller as 호출자
    participant Proxy as 프록시
    participant Interceptor as MethodValidationInterceptor
    participant Validator as ExecutableValidator
    participant Target as OrderServiceImpl

    Caller->>Proxy: placeOrder("", 3)
    Proxy->>Interceptor: invoke()
    Interceptor->>Validator: validateParameters(target, method, args, groups)
    Validator-->>Interceptor: violations (customerId blank)
    Interceptor-->>Caller: throw ConstraintViolationException
    Note over Target: invocation.proceed()가 호출되지 않음 - 대상 메서드 실행 안 됨

    Caller->>Proxy: findCustomerName("missing")
    Proxy->>Interceptor: invoke()
    Interceptor->>Validator: validateParameters(...)
    Validator-->>Interceptor: violations 없음
    Interceptor->>Target: invocation.proceed() - 실제 실행
    Target-->>Interceptor: null 반환
    Interceptor->>Validator: validateReturnValue(target, method, null, groups)
    Validator-->>Interceptor: violations (@NotNull 위반)
    Interceptor-->>Caller: throw ConstraintViolationException
    Note over Target: 이번엔 대상 메서드가 이미 실행된 뒤에 실패
```
