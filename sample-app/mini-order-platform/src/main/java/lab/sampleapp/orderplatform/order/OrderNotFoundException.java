package lab.sampleapp.orderplatform.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long orderId) {
        super("no order with id " + orderId);
    }
}
