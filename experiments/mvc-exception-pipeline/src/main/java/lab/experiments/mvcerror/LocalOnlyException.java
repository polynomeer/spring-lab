package lab.experiments.mvcerror;

// 컨트롤러 자신의 @ExceptionHandler만 이 예외를 처리한다 - @ControllerAdvice에는
// 대응하는 핸들러가 없다.
public class LocalOnlyException extends RuntimeException {

    public LocalOnlyException(String message) {
        super(message);
    }
}
