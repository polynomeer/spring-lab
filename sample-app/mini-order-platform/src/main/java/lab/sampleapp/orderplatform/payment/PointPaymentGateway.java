package lab.sampleapp.orderplatform.payment;

import org.springframework.stereotype.Component;

@Component
public class PointPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.POINT;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        if (request.amountWon() <= 0) {
            return new PaymentResult(false, null, "point amount must be positive");
        }
        return new PaymentResult(true, "POINT-TXN-" + request.memberId(), "point deducted");
    }
}
