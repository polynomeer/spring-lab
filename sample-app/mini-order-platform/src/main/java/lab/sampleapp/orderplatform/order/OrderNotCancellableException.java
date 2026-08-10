package lab.sampleapp.orderplatform.order;

public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(String message) {
        super(message);
    }
}
