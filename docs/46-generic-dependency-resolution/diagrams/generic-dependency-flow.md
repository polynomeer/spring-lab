# 제네릭 보존 vs 제네릭 소거 - "제외"가 아니라 "모호함"으로 실패하는 이유

[`generic-dependency-resolution.md`](../generic-dependency-resolution.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것.

```mermaid
flowchart TD
    A["consumer(Converter&lt;String, Integer&gt; converter) 파라미터 해석"] --> B{"후보 빈들의 @Bean 메서드가\n선언한 반환 타입"}

    B -->|"제네릭 보존\nConverter&lt;String,Integer&gt; / Converter&lt;Integer,String&gt;"| C["ResolvableType끼리 정확히 비교"]
    C --> D["stringToInt만 타입 인자 일치\nintToString은 불일치로 제외"]
    D --> E["후보 1개 - 정상 주입"]

    B -->|"제네릭 소거\n둘 다 원시 타입 Converter"| F["원시 타입은 제네릭 인자 요구와\n무관하게 매칭 가능 취급"]
    F --> G["stringToIntRaw, intToStringRaw\n둘 다 후보로 남음"]
    G --> H{"@Qualifier가 있는가?"}
    H -->|"없음"| I["NoUniqueBeanDefinitionException\n'found 2: stringToIntRaw,intToStringRaw'"]
    H -->|"있음(@Qualifier(\"stringToIntRaw\"))"| J["이름으로 즉시 좁혀짐 - 정상 주입"]

    style E fill:#161,color:#fff
    style I fill:#611,color:#fff
    style J fill:#161,color:#fff
```
