package lab.sampleapp.orderplatform.aop;

public class TransientOperationException extends RuntimeException {

    public TransientOperationException(String message) {
        super(message);
    }
}
