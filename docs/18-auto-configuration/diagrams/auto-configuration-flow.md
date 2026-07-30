# DeferredImportSelector의 지연 처리와 후보 재정렬

[`auto-configuration.md`](../auto-configuration.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 "왜 자동 설정이 사용자 설정보다 항상 나중에 평가되는가"를 결정하는 지연 처리 타이밍, 오른쪽은 후보 목록이 파일 순서에서 최종 순서로 재정렬되는 과정이다.

```mermaid
flowchart TD
    A["ConfigurationClassParser가<br/>@Configuration 클래스들을 파싱"] --> B{"@Import 대상이<br/>DeferredImportSelector인가?"}
    B -->|"아니오(일반 @Import)"| C["즉시 처리 - 그 자리에서<br/>BeanDefinition 등록"]
    B -->|"예(AutoConfigurationImportSelector)"| D["즉시 처리하지 않고<br/>deferredImportSelectors 목록에 등록만"]
    D --> E["나머지 사용자 @Configuration/@Bean도<br/>전부 이 과정으로 파싱·등록 완료"]
    E --> F["DeferredImportSelectorHandler#process()<br/>- 파싱 전체가 끝난 뒤 단 한 번"]
    F --> G["AutoConfigurationImportSelector가<br/>이제서야 후보를 탐색/필터링"]
    G --> H["@ConditionalOnMissingBean 평가<br/>- 사용자 빈이 이미 다 등록돼 있어<br/>정확하게 판단 가능"]

    style D fill:#333,color:#fff
    style H fill:#161,color:#fff
```

```mermaid
flowchart LR
    subgraph File[".imports 파일에 적힌 순서"]
        F1["GreetingAutoConfiguration"] --> F2["LoggingSupportAutoConfiguration"]
    end

    subgraph Sorter["AutoConfigurationSorter"]
        S["GreetingAutoConfiguration에<br/>@AutoConfiguration(after = LoggingSupportAutoConfiguration.class)<br/>선언 발견"]
        S --> S2["위상 정렬(topological sort) 적용"]
    end

    subgraph Result["실제 BeanDefinition 등록 순서"]
        R1["LoggingSupportAutoConfiguration"] --> R2["GreetingAutoConfiguration"]
    end

    File --> Sorter --> Result

    style S2 fill:#161,color:#fff
```
