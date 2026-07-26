package lab.experiments.configproxy;

public class AuditService {

    private final PaymentService paymentService;

    public AuditService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }
}
