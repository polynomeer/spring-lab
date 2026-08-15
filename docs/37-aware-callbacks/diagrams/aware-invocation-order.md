# 직접 호출 셋 vs BeanPostProcessor 위임 일곱, 그리고 등록 타이밍

[`aware-callbacks.md`](../aware-callbacks.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. 위쪽은 `initializeBean()` 안에서 두 그룹이 갈리는 지점, 아래쪽은 `ApplicationContextAwareProcessor`가 사용자 정의 `BeanPostProcessor` 빈보다 먼저 등록되는 `refresh()` 타이밍을 보여준다.

```mermaid
flowchart TD
    A["initializeBean(beanName, bean, mbd)"] --> B["invokeAwareMethods(beanName, bean)<br/>- BeanPostProcessor 전혀 관여 안 함"]
    B --> B1["BeanNameAware"] --> B2["BeanClassLoaderAware"] --> B3["BeanFactoryAware"]
    B3 --> C["applyBeanPostProcessorsBeforeInitialization(bean, beanName)"]
    C --> D["ApplicationContextAwareProcessor#postProcessBeforeInitialization"]
    D --> D1["EnvironmentAware"] --> D2["EmbeddedValueResolverAware"] --> D3["ResourceLoaderAware"]
    D3 --> D4["ApplicationEventPublisherAware"] --> D5["MessageSourceAware"] --> D6["ApplicationStartupAware"]
    D6 --> D7["ApplicationContextAware"]

    style B fill:#161,color:#fff
    style D fill:#333,color:#fff
```

```mermaid
flowchart LR
    subgraph Refresh["refresh() 단계 순서 (3주차)"]
        R1["1. prepareRefresh"] --> R2["2. prepareBeanFactory"]
        R2 --> R3["... obtainFreshBeanFactory 등"]
        R3 --> R6["6. registerBeanPostProcessors"]
    end

    R2 -.->|"beanFactory.addBeanPostProcessor(<br/>new ApplicationContextAwareProcessor(this))"| P1["ApplicationContextAwareProcessor<br/>등록 완료"]
    R6 -.->|"CustomAwareBeanPostProcessor 빈 생성<br/>(이미 등록된 P1의 적용 대상이 됨)"| P2["사용자 정의 BeanPostProcessor 빈들"]

    style P1 fill:#161,color:#fff
    style P2 fill:#333,color:#fff
```
