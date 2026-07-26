# orderService() 안에서 paymentService()를 호출하면 실제로 무슨 일이 일어나는가

[`configuration-bean.md`](../configuration-bean.md)의 6번(호출 흐름) 항목을 시각화한 것. `experiments/configuration-proxy-lab`의 `FullConfiguration`을 기준으로, `BeanMethodInterceptor.intercept()`가 두 가지 경우를 어떻게 다르게 처리하는지 그렸다.

```mermaid
sequenceDiagram
    participant Factory as AbstractAutowireCapableBeanFactory
    participant TL as SimpleInstantiationStrategy<br/>(ThreadLocal currentlyInvokedFactoryMethod)
    participant Proxy as FullConfiguration$$SpringCGLIB$$0
    participant Interceptor as BeanMethodInterceptor
    participant BF as BeanFactory

    Note over Factory,TL: 케이스 1 - 컨테이너가 orderService 빈을 처음 만드는 중
    Factory->>TL: currentlyInvokedFactoryMethod.set(orderService 메서드)
    Factory->>Proxy: orderService() 호출
    Proxy->>Interceptor: intercept(...)
    Interceptor->>TL: isCurrentlyInvokedFactoryMethod(orderService)?
    TL-->>Interceptor: true (바로 이 호출이 그 스레드로컬과 같은 Method)
    Interceptor->>Proxy: cglibMethodProxy.invokeSuper() → 진짜 메서드 바디 실행
    Note right of Proxy: 이 안에서 paymentService()를 호출
    Proxy->>Interceptor: intercept(paymentService 호출)
    Interceptor->>TL: isCurrentlyInvokedFactoryMethod(paymentService)?
    TL-->>Interceptor: false (지금 스레드로컬엔 orderService가 들어있음)
    Interceptor->>BF: resolveBeanReference → beanFactory.getBean("paymentService")
    BF-->>Interceptor: 캐시에 없으면 지금 생성, 있으면 그대로 반환
    Interceptor-->>Proxy: 그 결과를 paymentService() 호출 결과로 반환
    Factory->>TL: currentlyInvokedFactoryMethod.remove()

    Note over Factory,TL: 케이스 2 - 사용자 코드가 나중에 orderService()를 다시 부르면
    Factory->>Proxy: (예: 다른 @Bean 메서드가) orderService() 호출
    Proxy->>Interceptor: intercept(...)
    Interceptor->>TL: isCurrentlyInvokedFactoryMethod(orderService)?
    TL-->>Interceptor: false (컨테이너가 부른 게 아님)
    Interceptor->>BF: resolveBeanReference → beanFactory.getBean("orderService")
    BF-->>Interceptor: 이미 캐시된 싱글턴을 그대로 반환 (메서드 재실행 없음)
```

핵심은 `ThreadLocal<Method> currentlyInvokedFactoryMethod` 하나로 "지금 이 호출이 컨테이너 자신이 빈을 만들려고 부른 것인가, 아니면 누군가(사용자 코드 포함) 크로스 레퍼런스로 부른 것인가"를 구분한다는 것이다.
