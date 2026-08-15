# 세 가지 @Import 대상이 갈라지는 지점, 그리고 ImportAware의 별도 콜백 타이밍

[`import-selector.md`](../import-selector.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. `ConfigurationClassParser#processImports`가 `@Import` 값 하나하나를 `instanceof`로 구분해서 서로 다른 세 경로로 보내는 지점, 그리고 `ImportAware`가 일반 DI 경로가 아니라 별도의 `BeanPostProcessor` 콜백으로 처리되는 지점을 함께 그렸다.

```mermaid
flowchart TD
    A["@Import(X.class) 발견"] --> B{"X는 무엇인가?"}

    B -->|"ImportSelector"| C["selectImports(importingClassMetadata) 호출<br/>→ 클래스 이름 배열 반환"]
    C --> D["반환된 클래스들로 processImports()를<br/>재귀 호출 - 처음 만난 @Configuration과<br/>완전히 동일한 파이프라인을 다시 탐"]

    B -->|"ImportBeanDefinitionRegistrar"| E["configClass.addImportBeanDefinitionRegistrar()<br/>로 목록에만 추가 - 아직 실행 안 함"]
    E --> F["ConfigurationClassBeanDefinitionReader가<br/>나중에 registerBeanDefinitions() 호출<br/>→ BeanDefinitionRegistry에 직접 등록<br/>(재귀 파싱 없음 - 이 자체가 최종 단계)"]

    B -->|"평범한 @Configuration 클래스"| G["processConfigurationClass()로<br/>바로 파싱(@Bean, @ComponentScan,<br/>중첩된 @Import 등 전부 처리)"]

    style D fill:#333,color:#fff
    style F fill:#161,color:#fff
```

```mermaid
sequenceDiagram
    participant Parser as ConfigurationClassParser
    participant Registry as BeanFactory
    participant PP as ImportAwareBeanPostProcessor
    participant Holder as GreetingSettingsHolder

    Parser->>Parser: @ConfiguresGreeting(prefix="->")의<br/>AnnotationMetadata를 importStack에 기록
    Parser->>Registry: GreetingSettingsHolder를<br/>BeanDefinition으로 등록
    Registry->>Holder: 생성자 호출 (prefix 정보 없음)
    Registry->>PP: postProcessBeforeInitialization(holder, ...)
    PP->>PP: holder instanceof ImportAware?
    PP->>PP: importStack에서 이 클래스를<br/>가져온 메타데이터 조회
    PP->>Holder: setImportMetadata(metadata)
    Note over Holder: 이 시점에야 prefix="->"를 알게 됨
```
