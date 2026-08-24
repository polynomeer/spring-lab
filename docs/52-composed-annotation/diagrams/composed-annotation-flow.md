# 리플렉션이 못 보는 것을 Spring은 어떻게 보는가

[`composed-annotation.md`](../composed-annotation.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것.

```mermaid
flowchart TD
    A["LoggableService\n(@Loggable만 직접 붙어 있음)"] --> B{"조회 방법"}

    B -->|"JDK 표준 리플렉션\ngetAnnotation(Component.class)"| C["직접 붙은 애노테이션 배열만 확인\n@Loggable은 있지만 @Component 아님"]
    C --> D["null"]

    B -->|"Spring: AnnotatedElementUtils\n#findMergedAnnotation(Component.class)"| E["직접 붙은 애노테이션(@Loggable) 순회"]
    E --> F["각 애노테이션의 메타 애노테이션까지\n재귀 탐색"]
    F --> G["@Loggable 위에서 @Component 발견"]
    G --> H["속성을 합성(synthesize)한\n@Component 인스턴스 반환"]

    style D fill:#611,color:#fff
    style H fill:#161,color:#fff
```

```mermaid
flowchart LR
    subgraph Fast["@Fast 애노테이션 정의"]
        F1["value() default \"fast-lane\"\n@AliasFor(annotation=Qualifier.class,\nattribute=\"value\")"]
    end

    subgraph Usage["실제 사용"]
        U1["@Fast\n(값 미지정 → 기본값 사용)"]
        U2["@Fast(\"turbo\")\n(명시적 값)"]
    end

    subgraph Synthesized["AnnotatedElementUtils가 합성한 @Qualifier"]
        S1["@Qualifier(value=\"fast-lane\")"]
        S2["@Qualifier(value=\"turbo\")"]
    end

    F1 -.정의.-> U1
    F1 -.정의.-> U2
    U1 -->|"합성"| S1
    U2 -->|"합성"| S2

    S2 --> Q["QualifierAnnotationAutowireCandidateResolver\n#checkQualifiers"]
    Q --> R["engineConsumer의 @Fast(\"turbo\")와\nturboEngine의 합성 @Qualifier(\"turbo\")가 일치"]
    R --> Win["turboEngine 확정 선택"]

    style Win fill:#161,color:#fff
```
