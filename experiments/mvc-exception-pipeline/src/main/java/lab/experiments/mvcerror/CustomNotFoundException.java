package lab.experiments.mvcerror;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ExceptionHandler가 전혀 없어도 ResponseStatusExceptionResolver가 이 애노테이션만 보고
// 상태 코드를 정한다 - ExceptionHandlerExceptionResolver 체인에는 아무것도 등록하지 않는다.
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "widget not found")
public class CustomNotFoundException extends RuntimeException {

    public CustomNotFoundException(String message) {
        super(message);
    }
}
