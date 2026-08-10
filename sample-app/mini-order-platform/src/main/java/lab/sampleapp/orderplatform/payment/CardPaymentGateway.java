package lab.sampleapp.orderplatform.payment;

import org.springframework.stereotype.Component;

@Component
public class CardPaymentGateway implements PaymentGateway {

    private final PaymentGatewayClient client;

    public CardPaymentGateway(PaymentGatewayClient client) {
        this.client = client;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        String transactionId = client.authorize(request.amountWon());
        return new PaymentResult(true, transactionId, "card charged");
    }
}
