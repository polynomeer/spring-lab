package lab.sampleapp.orderplatform.web;

/**
 * spring-extensions/current-user-argument-resolver의 MissingCurrentUserException은
 * ResponseStatusException을 상속해서 Spring이 자동으로 상태 코드를 매핑하게 했지만,
 * 그 경로(ResponseStatusExceptionResolver)는 response.sendError()로 끝나 메시지
 * 컨버터를 아예 거치지 않는다 - 이 모듈의 모든 응답을 ApiResponse<T>로 감싸는 공통 응답
 * 규약(OrderResponseBodyAdvice)과 맞지 않는다. 그래서 이 모듈에서는 일반
 * RuntimeException으로 두고 OrderExceptionHandlers의 @ExceptionHandler로 직접
 * 처리해 나머지 에러 응답과 같은 포맷을 유지한다.
 */
public class MissingCurrentMemberException extends RuntimeException {

    public MissingCurrentMemberException() {
        super("no authenticated member for this request");
    }
}
