# 인자 해석과 반환값 처리, 그리고 ResponseBodyAdvice가 끼어드는 지점

[`controller-invocation.md`](../controller-invocation.md)의 6번(호출 흐름) 항목을 시각화한 것. 왼쪽은 인자 해석 파이프라인(15주차 HandlerMapping/HandlerAdapter 다음 단계), 오른쪽은 반환값이 두 갈래(뷰 vs `@ResponseBody`)로 갈리고 `ResponseBodyAdvice`가 그중 한쪽에만 끼어드는 지점이다.

```mermaid
flowchart TD
    A["InvocableHandlerMethod#invokeForRequest"] --> B["각 MethodParameter에 대해"]
    B --> C{"HandlerMethodArgumentResolverComposite<br/>먼저 등록된 순서로 지원 여부 확인"}
    C -->|지원하는 리졸버 없음| D["IllegalStateException(No suitable resolver)"]
    C -->|있음| E["resolveArgument() - 캐시에 기록 후 다음 요청부터 재사용"]
    E --> F["모든 파라미터 해석 완료 → doInvoke()<br/>(리플렉션으로 실제 컨트롤러 메서드 호출)"]
    F --> G["반환값 하나"]

    G --> H{"HandlerMethodReturnValueHandler가<br/>결정: 뷰? @ResponseBody?"}
    H -->|"뷰 이름/ModelAndView"| I["ViewResolver → View 렌더링<br/>(ResponseBodyAdvice 관여 안 함)"]
    H -->|"@ResponseBody/ResponseEntity"| J["RequestResponseBodyMethodProcessor<br/>#writeWithMessageConverters()"]
    J --> K{"이 프로세서가<br/>advice 목록을 갖고 있는가?"}
    K -->|"기본 생성자로 만들어짐(advice 없음)"| L["ResponseBodyAdvice 건너뜀 - 우리 프로젝트 26의 함정"]
    K -->|"advice 목록 포함 생성자"| M["ResponseBodyAdvice.beforeBodyWrite() 체인 적용"]
    L --> N["HttpMessageConverter로 직렬화"]
    M --> N

    style D fill:#611,color:#fff
    style L fill:#611,color:#fff
    style M fill:#161,color:#fff
```
