package lab.experiments.mvcerror;

// HighPriorityAdvice와 LowPriorityAdvice 둘 다 이 예외를 처리할 수 있다 - 어느 쪽이
// 이기는지는 순전히 @Order로 결정된다.
public class SharedFailureException extends RuntimeException {

    public SharedFailureException(String message) {
        super(message);
    }
}
