package lab.sampleapp.orderplatform.product;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(long productId, int requestedQuantity) {
        super("product " + productId + " does not have " + requestedQuantity + " unit(s) in stock");
    }
}
