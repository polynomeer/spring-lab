# 두 개의 캐시, 그리고 "&"가 그 사이에서 하는 일

[`factory-bean.md`](../factory-bean.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. `DefaultSingletonBeanRegistry`의 일반 싱글턴 캐시(4주차부터 알고 있던 것)와 `FactoryBeanRegistrySupport`의 `factoryBeanObjectCache`(이번 주 새로 확인한 것)가 서로 다른 계층에서 나란히 존재한다는 것, 그리고 `getBean()` 이름에 `&`가 있는지 없는지가 그 갈림길이라는 것을 보여준다.

```mermaid
flowchart TD
    A["context.getBean(name)"] --> B{"이름이 &로<br/>시작하는가?"}

    B -->|"아니오 - getBean(\"widget\")"| C["일반 싱글턴 캐시에서<br/>FactoryBean 원시 인스턴스를 조회<br/>(DefaultSingletonBeanRegistry, 4주차)"]
    C --> D{"그 인스턴스가<br/>FactoryBean인가?"}
    D -->|"예"| E["getObjectFromFactoryBean()"]
    E --> F{"factory.isSingleton()?"}
    F -->|"true"| G["factoryBeanObjectCache에서<br/>조회, 없으면 getObject() 후 저장<br/>→ 두 번째 호출부터는 재사용"]
    F -->|"false"| H["매번 getObject() 새로 실행<br/>→ 캐시에 저장 안 함"]

    B -->|"예 - getBean(\"&widget\")"| I["일반 싱글턴 캐시에서<br/>FactoryBean 원시 인스턴스 자체를<br/>그대로 반환 (getObject() 호출 안 함)"]

    style G fill:#161,color:#fff
    style H fill:#611,color:#fff
    style I fill:#333,color:#fff
```
