package lab.experiments.mvcerror;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

// HighPriorityAdvice(@Order(1))가 항상 먼저 조회되어 이 핸들러는 실행되지 않는다 -
// "핸들러가 매칭될 수 있다"는 것과 "실제로 실행된다"는 것이 다르다는 것을 보여주기 위한
// 대조군이다.
@ControllerAdvice
@Order(2)
public class LowPriorityAdvice {

    @ExceptionHandler(SharedFailureException.class)
    public ResponseEntity<String> handle(SharedFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("handled-by-low-priority-advice");
    }
}
