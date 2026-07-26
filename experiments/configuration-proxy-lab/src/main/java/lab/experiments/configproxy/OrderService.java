package lab.experiments.configproxy;

public class OrderService {

    private final PaymentService paymentService;

    public OrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }
}
