# 순환 참조 네 가지 경로

[`primary-qualifier-circular.md`](../primary-qualifier-circular.md)의 6번(호출 흐름) 항목을 시각화한 것. 같은 "A와 B가 서로를 참조"하는 상황이 주입 방식(생성자/setter)과 부가 요소(`@Lazy`, AOP 프록시)에 따라 완전히 다른 결과로 갈리는 지점을 보여준다.

```mermaid
flowchart TD
    A["A와 B가 서로를 참조"] --> B{"주입 방식은?"}

    B -->|"둘 다 생성자"| C{"한쪽에 @Lazy가<br/>있는가?"}
    C -->|아니오| D["A 생성 중 B 필요<br/>→ B 생성 중 A 필요<br/>→ A는 아직 인스턴스도 없음<br/>→ UnsatisfiedDependencyException"]
    C -->|"예 (한쪽만)"| E["@Lazy 쪽은 즉시<br/>지연 프록시로 채움<br/>→ 실제 조회는 최초 메서드 호출 시점으로 연기<br/>→ 어느 쪽이 먼저 만들어져도 성공"]

    B -->|"둘 다 setter/필드"| F{"어느 한쪽이<br/>AOP 어드바이스 대상인가?"}
    F -->|아니오| G["A 인스턴스 생성(생성자 인자 없음)<br/>→ addSingletonFactory(A) 등록<br/>→ B 생성 중 A 필요 →<br/>getEarlyBeanReference(A) = 원본 그대로<br/>→ 양쪽 setter 완료, 성공"]
    F -->|"예 (예: A)"| H["A 원본 생성 → addSingletonFactory(A) 등록<br/>→ B 생성 중 A 필요 →<br/>getEarlyBeanReference(A)가 AbstractAutoProxyCreator를 거쳐<br/>프록시를 미리 생성, earlyBeanReferences에 기록<br/>→ B는 이 프록시를 저장<br/>→ A의 postProcessAfterInitialization은<br/>earlyBeanReferences에 이미 있으므로 재감싸지 않음<br/>→ 최종 등록된 A == B가 들고 있는 조기 참조"]

    style D fill:#611,color:#fff
    style E fill:#161,color:#fff
    style G fill:#161,color:#fff
    style H fill:#161,color:#fff
```

```mermaid
sequenceDiagram
    participant Container as DefaultListableBeanFactory
    participant A as ProxiedCircularA(원본)
    participant AAPC as AbstractAutoProxyCreator
    participant B as ProxiedCircularB

    Container->>A: doCreateBean("A") 시작, 원본 인스턴스 생성
    Container->>Container: addSingletonFactory("A", () -> getEarlyBeanReference(A))
    Container->>B: populateBean("A") 중 B 필요 → doCreateBean("B")
    B->>Container: populateBean("B") 중 A 필요 → getBean("A")
    Container->>Container: singletonFactories에서 A의 팩토리 실행
    Container->>AAPC: getEarlyBeanReference(원본 A, "A")
    AAPC->>AAPC: wrapIfNecessary → 프록시 생성, earlyBeanReferences.put(cacheKey, 원본)
    AAPC-->>Container: 프록시 반환
    Container-->>B: 프록시를 A로 주입 (b.getA() == 이 프록시)
    B-->>Container: B 완성
    Container->>A: A의 setter에 B 주입, initializeBean(A) 진행
    Container->>AAPC: postProcessAfterInitialization(원본 A, "A")
    AAPC->>AAPC: earlyBeanReferences.remove(cacheKey) == 원본? → 예 → 다시 감싸지 않고 그 프록시를 그대로 반환
    AAPC-->>Container: 동일 프록시
    Container->>Container: "A"로 이 프록시를 최종 싱글턴 등록
    Note over Container,B: b.getA()가 들고 있던 프록시 == 컨테이너에 최종 등록된 A
```
