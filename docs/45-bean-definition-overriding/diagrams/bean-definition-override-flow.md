# 등록 순서가 승자를 결정하는 지점, 그리고 allowBeanDefinitionOverriding의 두 갈래

[`bean-definition-overriding.md`](../bean-definition-overriding.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것.

```mermaid
flowchart TD
    A["registerBeanDefinition(\"greeting\", 새 정의)"] --> B{"beanDefinitionMap에\n\"greeting\"이 이미 있는가?"}

    B -->|"아니오 (첫 등록)"| C["그냥 추가"]

    B -->|"예 (두 번째 등록)"| D["isBeanDefinitionOverridable(\"greeting\")\n= isAllowBeanDefinitionOverriding()\n= !Boolean.FALSE.equals(필드)"]

    D -->|"필드가 null(기본값) 또는 true"| E["logBeanDefinitionOverriding() - 로그만\nbeanDefinitionMap.put() - 조용히 교체"]
    E --> F["나중에 등록한 정의가 최종 승자"]

    D -->|"필드가 명시적으로 false"| G["throw BeanDefinitionOverrideException(\n  beanName, 새정의(getBeanDefinition),\n  기존정의(getExistingDefinition))"]
    G --> H["다른 포장 없이 refresh() 밖으로 그대로 전파"]

    style F fill:#333,color:#fff
    style H fill:#611,color:#fff
```
