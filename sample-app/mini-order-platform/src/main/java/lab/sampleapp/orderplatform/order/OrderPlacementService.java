package lab.sampleapp.orderplatform.order;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lab.sampleapp.orderplatform.event.OrderCompletedEvent;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.payment.PaymentRequest;
import lab.sampleapp.orderplatform.payment.PaymentResult;
import lab.sampleapp.orderplatform.product.InsufficientStockException;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductNotFoundException;
import lab.sampleapp.orderplatform.product.ProductRepository;

/**
 * 주문 금액은 클라이언트가 보내지 않는다 - {@link OrderItemRequest}는 상품 ID와 수량만 담고,
 * 단가/합계는 이 메서드가 {@link ProductRepository}에서 직접 조회해 계산한다. 클라이언트가
 * 가격을 부를 수 있게 하는 건 그 자체로 보안 결함이라는 걸 설계 시점에 반영한 것이다.
 *
 * <p>재고 차감({@link ProductRepository#decreaseStock})은 주문 저장/Outbox 저장과 같은
 * 트랜잭션(REQUIRED, 기본값) 안에서 일어난다 - 같은 DataSource/Connection을 쓰는 로컬
 * 트랜잭션의 원자성을 그대로 물려받는다(23주차 아웃박스 패턴과 동일한 근거). 여러 상품 중
 * 하나라도 재고가 부족하면 {@link InsufficientStockException}으로 전체 트랜잭션이
 * 롤백되어, 이미 차감된 다른 상품의 재고도 함께 복구된다("전부 아니면 전무"). 결제 시도는
 * 별도 REQUIRES_NEW 트랜잭션({@link PaymentHistoryRecorder})으로 분리해 뒀으므로, 결제
 * 실패로 이 메서드가 롤백되어도 "결제를 시도했다"는 이력만은 남는다.
 *
 * <p>{@link OrderCompletedEvent}는 이 메서드의 트랜잭션 "안에서" 발행되지만, 이 이벤트를
 * 구독하는 리스너들(Phase 5, lab.sampleapp.orderplatform.event 패키지)은 전부
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}이라 실제 실행은 이 메서드가
 * 정상적으로 커밋된 뒤로 미뤄진다.
 */
@Service
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ProductRepository productRepository;
    private final PaymentHistoryRecorder paymentHistoryRecorder;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public OrderPlacementService(
            OrderRepository orderRepository,
            OrderLineItemRepository orderLineItemRepository,
            OrderOutboxRepository outboxRepository,
            ProductRepository productRepository,
            PaymentHistoryRecorder paymentHistoryRecorder,
            IdGenerator idGenerator,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderLineItemRepository = orderLineItemRepository;
        this.outboxRepository = outboxRepository;
        this.productRepository = productRepository;
        this.paymentHistoryRecorder = paymentHistoryRecorder;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order placeOrder(String memberId, PaymentMethod method, List<OrderItemRequest> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("an order must contain at least one item");
        }

        long orderId = idGenerator.nextId();
        long totalAmountWon = 0;
        List<OrderLineItem> lineItems = new ArrayList<>();

        for (OrderItemRequest item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));

            if (!productRepository.decreaseStock(item.productId(), item.quantity())) {
                throw new InsufficientStockException(item.productId(), item.quantity());
            }

            totalAmountWon += product.priceWon() * item.quantity();
            lineItems.add(new OrderLineItem(
                    idGenerator.nextId(), orderId, product.id(), item.quantity(), product.priceWon()));
        }

        orderRepository.save(new Order(orderId, memberId, totalAmountWon, OrderStatus.PENDING));
        for (OrderLineItem lineItem : lineItems) {
            orderLineItemRepository.save(lineItem);
        }

        PaymentResult result = paymentHistoryRecorder.attemptAndRecord(
                orderId, method, new PaymentRequest(memberId, totalAmountWon));

        if (!result.success()) {
            // 이 예외로 인해 이 메서드의 트랜잭션은 롤백된다 - 방금 저장한 주문/라인 아이템,
            // 차감한 재고까지 전부 함께 사라진다. 하지만 paymentHistoryRecorder는 이미
            // REQUIRES_NEW로 독립 커밋했으므로 결제 시도 이력은 살아남는다.
            throw new PaymentFailedException(
                    "payment failed for order " + orderId + ": " + result.message());
        }

        orderRepository.updateStatus(orderId, OrderStatus.PAID);
        outboxRepository.save(new OrderOutboxEvent(
                idGenerator.nextId(), orderId, "ORDER_PAID", "order-paid:" + orderId, false));
        eventPublisher.publishEvent(new OrderCompletedEvent(orderId, memberId, totalAmountWon));

        return orderRepository.findById(orderId).orElseThrow();
    }
}
