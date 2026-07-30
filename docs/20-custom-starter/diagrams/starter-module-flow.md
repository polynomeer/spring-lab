# 3모듈 의존 방향과 ObjectProvider의 안전망 역할

[`custom-starter.md`](../custom-starter.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 세 모듈의 의존 방향(항상 아래에서 위로만), 오른쪽은 `@ConditionalOnMissingBean`이 불일치할 때 `ObjectProvider`가 어떻게 배선을 안전하게 지켜 주는지다.

```mermaid
flowchart BT
    Core["core<br/>RequestObservationInterceptor<br/>RequestObservationProperties<br/>(Boot 자동 설정 의존 없음)"]
    AutoConfig["autoconfigure<br/>RequestObservationAutoConfiguration<br/>(api로 core를 재노출)"]
    Starter["starter<br/>코드 없음 - api 의존성 선언만"]
    App["사용자 애플리케이션<br/>(starter 하나만 의존)"]

    Core -->|api| AutoConfig
    AutoConfig -->|api| Starter
    Starter -->|api| App

    style Core fill:#161,color:#fff
    style Starter fill:#333,color:#fff
```

```mermaid
flowchart TD
    A["@ConditionalOnProperty(enabled)"] --> B{"request-observation.enabled"}
    B -->|true 또는 미설정| C["RequestObservationInterceptor 빈 등록됨"]
    B -->|false| D["빈 자체가 없음"]

    C --> E["requestObservationWebMvcConfigurer(ObjectProvider<Interceptor> provider)"]
    D --> E

    E --> F{"provider.ifAvailable(...)"}
    F -->|"빈 있음(C 경로)"| G["registry.addInterceptor() 실행<br/>- 실제로 요청을 관찰함"]
    F -->|"빈 없음(D 경로)"| H["아무 일도 안 함<br/>- WebMvcConfigurer 빈 자체는<br/>여전히 등록되지만 무해하다"]

    style D fill:#611,color:#fff
    style H fill:#333,color:#fff
    style G fill:#161,color:#fff
```
