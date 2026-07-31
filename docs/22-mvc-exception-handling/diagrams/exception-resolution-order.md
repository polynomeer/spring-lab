# 예외 하나가 세 리졸버 중 어디서 멈추는가

[`mvc-exception-handling.md`](../mvc-exception-handling.md)의 6번(호출 흐름)·8번(런타임 관찰) 항목을 시각화한 것. `HandlerExceptionResolverComposite`는 등록된 순서대로 리졸버를 시도하다가, 하나라도 `ModelAndView`를 반환하면 그 자리에서 멈춘다 - 뒤쪽 리졸버는 앞쪽이 전부 손을 뗐을 때만 기회를 얻는다.

```mermaid
flowchart TD
    A["핸들러 실행 중 예외 발생"] --> B["HandlerExceptionResolverComposite"]

    B --> C["[1] ExceptionHandlerExceptionResolver"]
    C --> C1{"이 컨트롤러 자신의<br/>@ExceptionHandler가 매칭되는가?"}
    C1 -->|예| C2["즉시 실행 - advice는 확인조차 안 함<br/>(예: LocalOnlyException → 418)"]
    C1 -->|아니오| C3{"@ControllerAdvice 중<br/>매칭되는 게 있는가?<br/>(@Order로 이미 정렬됨)"}
    C3 -->|예, 여럿 매칭 가능| C4["order가 가장 작은 것 하나만 실행<br/>(예: SharedFailureException → 409, high-priority 승)"]
    C3 -->|아니오, 매칭 없음| D["[2] ResponseStatusExceptionResolver로"]

    D --> D1{"ResponseStatusException이거나<br/>@ResponseStatus 붙은 예외인가?"}
    D1 -->|예| D2["그 상태 코드 그대로 사용<br/>(예: ResponseStatusException → 402,<br/>@ResponseStatus 예외 → 404)"]
    D1 -->|아니오| E["[3] DefaultHandlerExceptionResolver로"]

    E --> E1{"Spring MVC 내부 예외 타입은?"}
    E1 -->|MethodArgumentTypeMismatchException| E2["400"]
    E1 -->|HttpMessageNotReadableException| E3["400"]
    E1 -->|MethodArgumentNotValidException| E4["400"]
    E1 -->|NoHandlerFoundException| E5["404"]
    E1 -->|HttpRequestMethodNotSupportedException| E6["405"]
    E1 -->|"그 외(알 수 없음)"| E7["리졸버 체인 전부 실패<br/>→ 500"]

    style C2 fill:#161,color:#fff
    style C4 fill:#161,color:#fff
    style D2 fill:#135,color:#fff
    style E7 fill:#611,color:#fff
```
