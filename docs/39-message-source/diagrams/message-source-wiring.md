# "messageSource"라는 이름 하나가 갈라놓는 두 경로, 그리고 부모로의 자동 위임

[`message-source.md`](../message-source.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. 30번(ConversionService)과 구조적으로 동일한 "정확한 이름 하나가 계약이다" 패턴을, 이번엔 부모-자식 컨텍스트 사이의 자동 위임까지 포함해서 그렸다.

```mermaid
flowchart TD
    A["refresh() 4번째 단계: initMessageSource()"] --> B{"beanFactory.containsLocalBean(\"messageSource\")?"}

    B -->|"예 - 이름이 정확히 일치"| C["그 빈을 그대로 this.messageSource로 사용"]
    C --> D["HierarchicalMessageSource이고<br/>부모가 아직 없으면<br/>부모 컨텍스트의 MessageSource를 자동 연결"]
    D --> E["getMessage() → 실제 ResourceBundleMessageSource<br/>로케일별 메시지 정상 해석"]

    B -->|"아니오 - 이름이 다르거나(myMessageSource)<br/>빈 자체가 없음"| F["new DelegatingMessageSource()"]
    F --> G["부모 컨텍스트의 MessageSource를<br/>parentMessageSource로 미리 연결"]
    G --> H["같은 이름(\"messageSource\")으로<br/>registerSingleton() - 원래 있던<br/>다른 이름의 빈은 여전히 무시됨"]
    H --> I{"부모가 있는가?"}
    I -->|"예"| J["getMessage() → 부모에게 그대로 위임<br/>(자식은 아무것도 안 해도 부모 메시지를 씀)"]
    I -->|"아니오"| K["getMessage() → 기본 메시지 없으면 null/예외"]

    style C fill:#161,color:#fff
    style F fill:#333,color:#fff
    style J fill:#161,color:#fff
    style K fill:#611,color:#fff
```
