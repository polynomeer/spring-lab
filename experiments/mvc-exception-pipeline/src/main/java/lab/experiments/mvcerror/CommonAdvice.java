package lab.experiments.mvcerror;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonAdvice {

    @ExceptionHandler(AdviceOnlyException.class)
    public ResponseEntity<String> handle(AdviceOnlyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("handled-by-advice:" + ex.getMessage());
    }
}
