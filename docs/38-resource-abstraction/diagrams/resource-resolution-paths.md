# 같은 문자열, 다른 컨텍스트 - getResourceByPath 오버라이드 하나가 갈라놓는 경로

[`resource-abstraction.md`](../resource-abstraction.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것.

```mermaid
flowchart TD
    A["context.getResource(\"lab/experiments/resource/greeting.txt\")\n(접두어 없음)"] --> B{"어떤 ApplicationContext?"}

    B -->|"AnnotationConfigApplicationContext\n(getResourceByPath 오버라이드 없음)"| C["DefaultResourceLoader의 기본 구현\ngetResourceByPath()"]
    C --> D["new ClassPathContextResource(path, classLoader)"]
    D --> E["실제 테스트 리소스를 찾아냄\nexists() == true"]

    B -->|"FileSystemXmlApplicationContext\n(getResourceByPath 오버라이드함)"| F["오버라이드된 getResourceByPath()"]
    F --> G["new FileSystemResource(path)"]
    G --> H["파일 시스템에 그런 경로 없음\nexists() == false"]

    style D fill:#161,color:#fff
    style G fill:#333,color:#fff
```

```mermaid
flowchart LR
    R1["ClassPathResource(\".../Configuration.class\")"] --> R2{"getURL()의 프로토콜이 file:인가?"}
    R2 -->|"예 (jar 밖 디렉터리)"| R3["getFile() 성공"]
    R2 -->|"아니오 (jar: 프로토콜)"| R4["getFile() → FileNotFoundException"]

    R1 --> R5["getInputStream()"]
    R5 --> R6["항상 성공 - 클래스로더가\n스트림을 열어 주기만 하면 됨"]

    style R3 fill:#161,color:#fff
    style R4 fill:#611,color:#fff
    style R6 fill:#161,color:#fff
```
