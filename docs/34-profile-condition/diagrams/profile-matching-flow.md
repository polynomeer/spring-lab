# 배열(OR) vs 문자열 안 연산자(AND), 그리고 default 폴백의 게이트

[`profile-condition.md`](../profile-condition.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. 왼쪽은 `@Profile({"dev","test"})`(OR)와 `@Profile("prod & cloud")`(AND)가 왜 다른 경로를 타는지, 오른쪽은 `default` 폴백이 활성 프로파일 유무 하나로 완전히 켜지고 꺼지는 것을 보여준다.

```mermaid
flowchart TD
    subgraph OR["@Profile({\"dev\", \"test\"}) - 배열"]
        A1["ProfileCondition#matches"] --> A2["value 배열을 순회<br/>(각 원소가 독립된 matchesProfiles 호출)"]
        A2 --> A3{"dev 활성?"}
        A3 -->|"예"| A4["즉시 true 반환 - OR"]
        A3 -->|"아니오"| A5{"test 활성?"}
        A5 -->|"예"| A4
        A5 -->|"아니오"| A6["false"]
    end

    subgraph AND["@Profile(\"prod & cloud\") - 한 문자열"]
        B1["ProfileCondition#matches"] --> B2["value 배열의 원소 하나:<br/>\"prod & cloud\""]
        B2 --> B3["Profiles.of(\"prod & cloud\")<br/>→ AND 표현식 트리로 파싱"]
        B3 --> B4{"prod 활성 && cloud 활성?"}
        B4 -->|"둘 다 true"| B5["true"]
        B4 -->|"하나라도 false"| B6["false"]
    end
```

```mermaid
flowchart LR
    C1["isProfileActive(\"default\")"] --> C2{"activeProfiles.isEmpty()?"}
    C2 -->|"예 - 활성 프로파일 전혀 없음"| C3["defaultProfiles.contains(\"default\")?<br/>→ true (기본값이 {\"default\"}이므로)"]
    C2 -->|"아니오 - dev든 prod든 뭔가 하나라도 활성"| C4["defaultProfiles는 아예 확인하지 않음<br/>→ false"]

    style C3 fill:#161,color:#fff
    style C4 fill:#611,color:#fff
```
