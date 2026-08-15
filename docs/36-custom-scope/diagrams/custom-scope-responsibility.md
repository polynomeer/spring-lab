# 컨테이너가 넘겨주는 것 vs TenantScope가 스스로 결정하는 것

[`custom-scope.md`](../custom-scope.md)의 6번(호출 흐름)·9번(공식 소스 확인) 항목을 시각화한 것. 컨테이너(`AbstractBeanFactory`)가 `ObjectFactory`와 소멸 `Runnable`을 "만들어서 넘겨주는" 지점까지는 공통이지만, 그걸 캐싱할지·언제 실행할지는 전부 `TenantScope` 내부의 결정이라는 경계를 보여준다.

```mermaid
flowchart TD
    subgraph Container["컨테이너(AbstractBeanFactory)가 하는 일"]
        A["doGetBean() - mbd.getScope() = \"tenant\""] --> B["등록된 Scope 찾기"]
        B --> C["createBean()을 감싼<br/>ObjectFactory 하나를 만들어<br/>scope.get(name, objectFactory)로 전달"]
        D["registerDisposableBeanIfNecessary()"] --> E["DisposableBean/@PreDestroy를 감싼<br/>Runnable 하나를 만들어<br/>scope.registerDestructionCallback(name, runnable)로 전달"]
    end

    subgraph TenantScope["TenantScope가 스스로 결정하는 일"]
        F["get() 안에서:<br/>이미 만든 적 있으면 캐시에서 반환,<br/>없으면 그제서야 objectFactory.getObject() 호출"]
        G["registerDestructionCallback() 안에서:<br/>Runnable을 그냥 저장만 해 둠 - 실행 안 함"]
        H["endTenant() 호출 시점에야:<br/>저장해 뒀던 Runnable들을 실제로 실행"]
    end

    C --> F
    E --> G
    G -.->|"애플리케이션 코드가<br/>명시적으로 호출"| H

    I["context.close()"] -.->|"DefaultSingletonBeanRegistry만 정리<br/>- TenantScope는 전혀 안 건드림"| J["destroy() 여전히 미호출"]

    style F fill:#161,color:#fff
    style H fill:#161,color:#fff
    style J fill:#611,color:#fff
```
