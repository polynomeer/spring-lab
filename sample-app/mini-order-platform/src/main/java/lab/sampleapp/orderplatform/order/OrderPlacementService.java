package lab.sampleapp.orderplatform.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.sampleapp.orderplatform.event.OrderCompletedEvent;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.payment.PaymentRequest;
import lab.sampleapp.orderplatform.payment.PaymentResult;

/**
 * 주문 저장과 Outbox 이벤트 저장은 이 메서드의 트랜잭션(REQUIRED, 기본값) 안에서 함께
 * 커밋되거나 함께 롤백된다 - 같은 DataSource/Connection을 쓰는 로컬 트랜잭션의 원자성을
 * 그대로 물려받는다(23주차 아웃박스 패턴과 동일한 근거). 결제 시도는 별도 REQUIRES_NEW
 * 트랜잭션({@link PaymentHistoryRecorder})으로 분리해 뒀으므로, 결제 실패로 이 메서드가
 * 롤백되어도 "결제를 시도했다"는 이력만은 남는다.
 *
 * <p>{@link OrderCompletedEvent}는 이 메서드의 트랜잭션 "안에서" 발행되지만, 이 이벤트를
 * 구독하는 리스너들(Phase 5, lab.sampleapp.orderplatform.event 패키지)은 전부
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}이라 실제 실행은 이 메서드가
 * 정상적으로 커밋된 뒤로 미뤄진다 - 결제가 실패해 이 메서드가 롤백되면(위의
 * PaymentFailedException 경로) 이 publishEvent() 호출 자체가 실행되지 않으므로 알림도,
 * Outbox 발행 시도도 애초에 일어나지 않는다.
 */
@Service
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final PaymentHistoryRecorder paymentHistoryRecorder;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public OrderPlacementService(
            OrderRepository orderRepository,
            OrderOutboxRepository outboxRepository,
            PaymentHistoryRecorder paymentHistoryRecorder,
            IdGenerator idGenerator,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.paymentHistoryRecorder = paymentHistoryRecorder;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order placeOrder(String memberId, PaymentMethod method, long amountWon) {
        long orderId = idGenerator.nextId();
        orderRepository.save(new Order(orderId, memberId, amountWon, OrderStatus.PENDING));

        PaymentResult result = paymentHistoryRecorder.attemptAndRecord(
                orderId, method, new PaymentRequest(memberId, amountWon));

        if (!result.success()) {
            // 이 예외로 인해 이 메서드의 트랜잭션은 롤백된다 - 방금 저장한 주문(PENDING)도
            // 함께 사라진다. 하지만 paymentHistoryRecorder는 이미 REQUIRES_NEW로 독립
            // 커밋했으므로 결제 시도 이력은 살아남는다.
            throw new PaymentFailedException(
                    "payment failed for order " + orderId + ": " + result.message());
        }

        orderRepository.updateStatus(orderId, OrderStatus.PAID);
        outboxRepository.save(new OrderOutboxEvent(
                idGenerator.nextId(), orderId, "ORDER_PAID", "order-paid:" + orderId, false));
        eventPublisher.publishEvent(new OrderCompletedEvent(orderId, memberId, amountWon));

        return orderRepository.findById(orderId).orElseThrow();
    }
}
