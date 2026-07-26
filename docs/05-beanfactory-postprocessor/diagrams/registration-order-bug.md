# addBeanFactoryPostProcessor()로 등록한 BDRPP가 스캔보다 먼저 실행되는 이유

[`beanfactory-postprocessor.md`](../beanfactory-postprocessor.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것 — 처음 겪었던 버그(제거가 조용히 안 먹힌 것)의 원인을 그대로 그렸다.

```mermaid
flowchart TD
    A["context.refresh()"] --> B["invokeBeanFactoryPostProcessors()"]
    B --> C["PostProcessorRegistrationDelegate<br/>.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors())"]

    C --> D["1단계: getBeanFactoryPostProcessors() 목록 순회<br/>(context.addBeanFactoryPostProcessor()로 넘긴 것들)"]
    D -->|BDRPP면| E["postProcessBeanDefinitionRegistry() 즉시 호출"]
    E -.버그 발생 지점.-> F["ExclusionRegistryPostProcessor 실행<br/>→ 아직 아무것도 스캔 안 됨<br/>→ excludedService가 등록소에 없음<br/>→ 제거할 대상이 없어 조용히 통과"]

    D --> G["2단계: 빈으로 등록된 BeanDefinitionRegistryPostProcessor 탐색<br/>(PriorityOrdered → Ordered → 나머지, 재귀적으로)"]
    G --> H["ConfigurationClassPostProcessor 실행<br/>(PriorityOrdered) → @ComponentScan 수행<br/>→ 이제야 excludedService 등록"]
    G --> I["ExclusionRegistryPostProcessor를 빈으로 등록했다면<br/>여기(나머지 그룹)에서 실행됨<br/>→ excludedService가 이미 있어 정상 제거"]

    D --> J["3단계: 지금까지의 모든 BDRPP.postProcessBeanFactory() 호출"]
    J --> K["4단계: 나머지 일반 BeanFactoryPostProcessor.postProcessBeanFactory() 호출<br/>(BeanDefinitionAnnotationRewriter 여기)"]

    style F fill:#611,color:#fff
    style I fill:#163,color:#fff
```

수정 전(왼쪽 경로, `addBeanFactoryPostProcessor`)과 수정 후(오른쪽 경로, `registerBean`)의 차이는 딱 하나 — **어느 단계에서 발견되느냐**다. 코드 자체는 똑같다.
