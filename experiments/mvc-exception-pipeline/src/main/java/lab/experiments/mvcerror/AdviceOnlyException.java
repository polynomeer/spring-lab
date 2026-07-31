package lab.experiments.mvcerror;

// 컨트롤러에는 이 예외를 처리하는 @ExceptionHandler가 없다 - CommonAdvice(@ControllerAdvice)만
// 처리한다.
public class AdviceOnlyException extends RuntimeException {

    public AdviceOnlyException(String message) {
        super(message);
    }
}
