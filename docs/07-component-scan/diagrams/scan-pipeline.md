# ComponentScanner.scan()의 내부 파이프라인

[`component-scan.md`](../component-scan.md)의 6번(호출 흐름) 항목을 시각화한 것.

```mermaid
flowchart TD
    A["scan(basePackage...)"] --> B["각 basePackage에 대해 findClasses()"]
    B --> C["classLoader.getResources(패키지 경로)"]
    C --> D["디렉터리 재귀 순회 → .class 파일 수집"]
    D --> E["Class.forName(name, initialize=false, classLoader)"]
    E --> F["isEligible(class)?"]
    F -->|"interface/abstract"| G["제외"]
    F -->|"@MiniComponent 없고<br/>include filter도 불일치"| G
    F -->|"exclude filter 일치"| G
    F -->|"통과"| H["resolveBeanName(class)"]
    H --> I{"@MiniComponent.value()<br/>가 있는가?"}
    I -->|"있음"| J["그 값을 이름으로"]
    I -->|"없음"| K["BeanNameGenerator.generateName()<br/>(기본: decapitalize)"]
    J --> L["result.putIfAbsent(name, class)"]
    K --> L
    L --> M{"이미 다른 클래스가<br/>같은 이름을 썼는가?"}
    M -->|예| N["DuplicateComponentNameException"]
    M -->|아니오| O["Map&lt;String, Class&lt;?&gt;&gt;에 누적"]

    style E fill:#611,color:#fff
    style N fill:#611,color:#fff
```

`E`(빨간 상자)가 실제 Spring과 가장 크게 갈라지는 지점이다 — 우리는 클래스를 **로딩**하고, Spring의 `MetadataReader`는 바이트코드만 **읽는다** (11번 참고).
