# catch 블록 순서 하나가 "없음"과 "모호함"을 갈라놓는 지점

[`object-provider.md`](../object-provider.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. `getIfAvailable()`은 `NoUniqueBeanDefinitionException`을 먼저(더 구체적으로) 잡아서 다시 던지고, `getIfUnique()`는 그 가드가 아예 없어서 상위 타입 catch 하나가 두 실패를 모두 흡수한다.

```mermaid
flowchart TD
    A["getObject() 호출"] --> B{"후보 개수"}
    B -->|"0개"| C["NoSuchBeanDefinitionException"]
    B -->|"1개(또는 @Primary로 해소됨)"| D["그 빈 반환"]
    B -->|"여러 개, 모호함"| E["NoUniqueBeanDefinitionException<br/>(NoSuchBeanDefinitionException의 하위 클래스)"]

    subgraph IfAvailable["getIfAvailable()"]
        C --> F["catch (NoSuchBeanDefinitionException)<br/>→ null"]
        E --> G["catch (NoUniqueBeanDefinitionException) 먼저 매치<br/>→ throw ex (다시 던짐)"]
    end

    subgraph IfUnique["getIfUnique()"]
        C --> H["catch (NoSuchBeanDefinitionException)<br/>→ null"]
        E --> H
    end

    style G fill:#611,color:#fff
    style H fill:#161,color:#fff
```
