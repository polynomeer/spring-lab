# 수동 BeanPostProcessor와 자동 프록시 생성기, 같은 판정 로직을 부르는 두 경로

[`auto-proxy-creator.md`](../auto-proxy-creator.md)의 6번(호출 흐름) 항목을 시각화한 것. 두 구현 모두 결국 `AopUtils.canApply()`(mini는 그에 대응하는 `hasEligibleMethod()`)와 `ProxyFactory`(mini는 `MiniProxyFactory`)로 수렴한다 — 차이는 "누가, 어느 시점에 그 판정을 호출하는가"뿐이다.

```mermaid
flowchart TD
    subgraph Manual["수동 - MethodTimingBeanPostProcessor"]
        M1["빈 초기화 완료"] --> M2["postProcessAfterInitialization(bean, name)"]
        M2 --> M3{"AopUtils.canApply(advisor, bean.getClass())?"}
        M3 -->|아니오| M4["원본 bean 그대로 반환"]
        M3 -->|예| M5["new ProxyFactory(bean); addAdvisor(advisor)"]
        M5 --> M6["proxyFactory.getProxy() 반환"]
    end

    subgraph Auto["자동 - DefaultAdvisorAutoProxyCreator"]
        A1["빈 초기화 완료"] --> A2["postProcessAfterInitialization(bean, name)"]
        A2 --> A3{"isInfrastructureClass(beanClass)?<br/>(Advice/Pointcut/Advisor/AopInfrastructureBean)"}
        A3 -->|예| A4["원본 bean 그대로 반환 (프록시 후보에서 제외)"]
        A3 -->|아니오| A5["findCandidateAdvisors()<br/>= BeanFactoryAdvisorRetrievalHelper.findAdvisorBeans()<br/>(컨테이너의 모든 Advisor 빈 조회)"]
        A5 --> A6["findAdvisorsThatCanApply(candidates, beanClass)<br/>→ 내부적으로 AopUtils.canApply() 반복 호출"]
        A6 --> A7{"매칭되는 Advisor가 있는가?"}
        A7 -->|없음| A4
        A7 -->|있음| A8["new ProxyFactory(bean); 매칭된 Advisor들 addAdvisor"]
        A8 --> A9["proxyFactory.getProxy() 반환"]
    end

    M3 -.같은 유틸리티.- A6

    style M3 fill:#333,color:#fff
    style A6 fill:#333,color:#fff
    style A3 fill:#611,color:#fff
```
