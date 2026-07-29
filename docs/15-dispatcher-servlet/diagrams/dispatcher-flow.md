# doDispatch 한 번의 호출 흐름과 갈라지는 지점들

[`dispatcher-servlet.md`](../dispatcher-servlet.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 `doDispatch()` 한 번의 전체 경로, 오른쪽은 인터셉터 체인이 preHandle 중단/postHandle 생략을 어떻게 처리하는지다.

```mermaid
flowchart TD
    A["HTTP 요청 도착"] --> B["getHandler(request)<br/>여러 HandlerMapping을 order 순서로 조회"]
    B --> C{"매칭되는<br/>HandlerExecutionChain?"}
    C -->|없음| D["noHandlerFound()<br/>throwExceptionIfNoHandlerFound=false → 바로 404"]
    C -->|있음| E["getHandlerAdapter(handler)<br/>핸들러 형태에 맞는 HandlerAdapter 선택"]
    E --> F["mappedHandler.applyPreHandle()"]
    F -->|"인터셉터 하나라도 false"| G["즉시 중단 - 통과했던<br/>인터셉터들만 afterCompletion 호출"]
    F -->|모두 true| H["ha.handle() - 실제 컨트롤러 호출"]
    H -->|예외| I["dispatchException에 저장<br/>postHandle은 건너뜀"]
    H -->|정상| J["applyPostHandle()<br/>등록 역순으로 실행"]
    I --> K["processDispatchResult<br/>ExceptionResolver가 처리 못 하면 그대로 재던짐"]
    J --> K

    style D fill:#611,color:#fff
    style G fill:#611,color:#fff
    style I fill:#611,color:#fff
```

```mermaid
sequenceDiagram
    participant DS as DispatcherServlet
    participant Chain as HandlerExecutionChain
    participant I1 as Interceptor1 (preHandle=true)
    participant I2 as Interceptor2 (preHandle=false)
    participant Ctrl as Controller

    DS->>Chain: applyPreHandle()
    Chain->>I1: preHandle()
    I1-->>Chain: true (interceptorIndex=0)
    Chain->>I2: preHandle()
    I2-->>Chain: false
    Chain->>Chain: triggerAfterCompletion(interceptorIndex=0)
    Chain->>I1: afterCompletion()
    Note over I1,Ctrl: Controller는 전혀 호출되지 않는다 -<br/>하지만 I1의 afterCompletion은 실행된다
    Chain-->>DS: false
    DS->>DS: return (더 이상 진행 안 함)
