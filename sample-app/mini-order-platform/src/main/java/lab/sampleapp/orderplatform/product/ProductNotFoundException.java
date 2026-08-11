package lab.sampleapp.orderplatform.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("no product with id " + productId);
    }
}
