# 생성자 선택과 후보 선택, 두 개의 결정 트리

[`dependency-resolution.md`](../dependency-resolution.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 "어떤 생성자를 쓸 것인가", 오른쪽은 "그 생성자의 파라미터 하나를 무엇으로 채울 것인가"라는, 서로 다른 두 단계의 결정이다.

```mermaid
flowchart TD
    A["빈 클래스의 생성자들"] --> B{"생성자가 1개?"}
    B -->|예| C["그 생성자 사용<br/>(@Autowired/@MiniAutowired 불필요)"]
    B -->|아니오| D{"@Autowired(required=true)가<br/>2개 이상?"}
    D -->|예| E["BeanCreationException<br/>(시작 시점에 실패)"]
    D -->|아니오| F{"@Autowired가<br/>정확히 1개?"}
    F -->|예| G["그 생성자 사용<br/>(의존성 없으면 required=false일 때만 기본 생성자로 폴백)"]
    F -->|아니오, 0개| H{"기본 생성자가<br/>있는가?"}
    H -->|예| I["기본 생성자 사용<br/>(다른 생성자의 파라미터는 완전히 무시됨)"]
    H -->|아니오| J["후보를 정하지 못함<br/>(mini: AmbiguousConstructorException)"]

    style D fill:#611,color:#fff
    style E fill:#611,color:#fff
    style H fill:#611,color:#fff
    style I fill:#611,color:#fff
```

```mermaid
flowchart TD
    A["주입 지점 (파라미터/필드) 하나"] --> B["타입으로 후보 빈 검색"]
    B --> C{"후보 개수는?"}
    C -->|0| D["Optional/List/ObjectProvider면 관용<br/>아니면 UnsatisfiedDependencyException"]
    C -->|1| E["그 후보 사용"]
    C -->|2개 이상| F{"@Primary가<br/>정확히 1개?"}
    F -->|예| E
    F -->|아니오| G{"파라미터/필드 이름이<br/>후보 빈 이름과 일치?"}
    G -->|예| E
    G -->|아니오| H{"@Qualifier 지정 이름이<br/>후보 빈 이름과 일치?"}
    H -->|예| E
    H -->|아니오| I["NoUniqueBeanDefinitionException"]

    style G fill:#333,color:#fff
    style H fill:#333,color:#fff
```

두 번째 트리에서 "이름 일치"(G)가 "`@Qualifier`"(H)보다 먼저 검사된다는 순서는 `DefaultListableBeanFactory#determineAutowireCandidate` 소스를 직접 읽고서야 확인했다(9번 참고) — 실무에서 거의 부딪히지 않는 순서지만, 파라미터 이름이 우연히 다른 빈 이름과 겹치면 `@Qualifier`보다 먼저 이 규칙이 이긴다.
