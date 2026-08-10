package lab.sampleapp.orderplatform.web;

public class UnsupportedPaymentMethodException extends RuntimeException {

    public UnsupportedPaymentMethodException(String rawValue) {
        super("unsupported payment method: " + rawValue);
    }
}
