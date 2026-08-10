package lab.sampleapp.orderplatform.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.payment.PaymentProcessingService;
import lab.sampleapp.orderplatform.payment.PaymentRequest;
import lab.sampleapp.orderplatform.payment.PaymentResult;

/**
 * OrderPlacementService.placeOrder()의 트랜잭션과 별개로 커밋되는 REQUIRES_NEW 트랜잭션.
 * 주문 저장에 실패(롤백)해도 "결제를 시도했다는 사실 자체"는 남아야 한다는 요구를
 * 반영한다 - 이 클래스가 OrderPlacementService의 메서드가 아니라 별도 빈이어야 하는 이유이기도
 * 하다: Spring AOP는 프록시 기반이라 같은 빈 안에서의 self-invocation(this.method())은
 * 프록시를 거치지 않아 @Transactional이 전혀 적용되지 않는다(21주차 Transaction Propagation
 * Playground에서 이미 확인한 함정).
 */
@Component
public class PaymentHistoryRecorder {

    private final PaymentProcessingService paymentProcessingService;
    private final PaymentHistoryRepository historyRepository;
    private final IdGenerator idGenerator;

    public PaymentHistoryRecorder(
            PaymentProcessingService paymentProcessingService,
            PaymentHistoryRepository historyRepository,
            IdGenerator idGenerator) {
        this.paymentProcessingService = paymentProcessingService;
        this.historyRepository = historyRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResult attemptAndRecord(long orderId, PaymentMethod method, PaymentRequest request) {
        PaymentResult result = paymentProcessingService.processPayment("order-" + orderId, method, request);
        historyRepository.save(new PaymentHistoryEntry(
                idGenerator.nextId(), orderId, method, request.amountWon(), result.success(), result.transactionId()));
        return result;
    }
}
