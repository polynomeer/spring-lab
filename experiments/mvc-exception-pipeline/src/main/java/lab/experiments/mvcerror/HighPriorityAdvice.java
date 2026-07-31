package lab.experiments.mvcerror;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

// ControllerAdviceBean.findAnnotatedBeans()가 OrderComparator.sort()로 advice 빈들을
// 정렬해 두므로, 같은 예외를 처리할 수 있는 advice가 여럿이면 order가 더 작은 이쪽이 먼저
// 조회되어 이긴다 - LowPriorityAdvice의 핸들러는 절대 호출되지 않는다.
@ControllerAdvice
@Order(1)
public class HighPriorityAdvice {

    @ExceptionHandler(SharedFailureException.class)
    public ResponseEntity<String> handle(SharedFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("handled-by-high-priority-advice");
    }
}
