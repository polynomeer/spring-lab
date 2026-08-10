package lab.sampleapp.orderplatform.payment;

import org.springframework.stereotype.Component;

import lab.sampleapp.orderplatform.aop.Audited;
import lab.sampleapp.orderplatform.aop.IdempotencyGuarded;
import lab.sampleapp.orderplatform.aop.Retryable;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.aop.RequiresRole;
import lab.sampleapp.orderplatform.aop.Timed;

/**
 * Phase 1의 PaymentGatewayRegistry/PaymentGatewayClient 위에 Phase 2의 AOP 관심사를
 * 얹은 파사드. 세 메서드가 서로 다른 어드바이스 조합을 보여준다 - 어떤 조합을 어떤 순서로
 * 쌓을지는 각 aop 클래스의 @Order 주석에서 설명한다.
 */
@Component
public class PaymentProcessingService {

    private final PaymentGatewayRegistry registry;
    private final PaymentGatewayClient client;

    public PaymentProcessingService(PaymentGatewayRegistry registry, PaymentGatewayClient client) {
        this.registry = registry;
        this.client = client;
    }

    @Timed
    @Audited(action = "process-payment")
    @IdempotencyGuarded
    public PaymentResult processPayment(String idempotencyKey, PaymentMethod method, PaymentRequest request) {
        return registry.charge(method, request);
    }

    @Timed
    @Audited(action = "refund")
    @RequiresRole(Role.ADMIN)
    public PaymentResult refund(PaymentRequest request) {
        if (request.amountWon() <= 0) {
            return new PaymentResult(false, null, "refund amount must be positive");
        }
        return new PaymentResult(true, "REFUND-TXN-" + request.memberId(), "refunded");
    }

    @Timed
    @Retryable(maxAttempts = 3)
    public void pingProvider() {
        client.ping();
    }
}
