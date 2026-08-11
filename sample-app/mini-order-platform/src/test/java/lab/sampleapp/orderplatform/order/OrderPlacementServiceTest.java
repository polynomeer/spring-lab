package lab.sampleapp.orderplatform.order;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.UnexpectedRollbackException;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3(트랜잭션) 검증. 매 테스트가 새 컨텍스트(따라서 새 임베디드 H2 + 1부터 다시 시작하는
 * IdGenerator)를 만들기 때문에, 그 테스트 안에서 처음 발급되는 주문 ID는 항상 1이다.
 */
class OrderPlacementServiceTest {

    private AnnotationConfigApplicationContext context;

    private AnnotationConfigApplicationContext buildContext() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        context.getBean(CurrentActor.class).set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));
        return context;
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private static Product seedProduct(
            AnnotationConfigApplicationContext ctx, long id, long priceWon, int stock) {
        Product product = new Product(id, "product-" + id, priceWon, stock);
        ctx.getBean(ProductRepository.class).save(product);
        return product;
    }

    @Test
    void placingAnOrderWithSuccessfulPaymentCommitsOrderPaymentHistoryAndOutboxTogether() {
        AnnotationConfigApplicationContext ctx = buildContext();
        seedProduct(ctx, 501L, 10_000, 5);
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        Order placed = service.placeOrder(
                "cust-1", PaymentMethod.CARD, List.of(new OrderItemRequest(501L, 1)));

        assertThat(placed.status()).isEqualTo(OrderStatus.PAID);
        assertThat(placed.amountWon()).isEqualTo(10_000); // 클라이언트가 아니라 Product 가격에서 계산됨

        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        assertThat(orderRepository.findById(placed.id())).contains(placed);

        OrderLineItemRepository lineItemRepository = ctx.getBean(OrderLineItemRepository.class);
        assertThat(lineItemRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(item -> item.productId() == 501L && item.quantity() == 1 && item.unitPriceWon() == 10_000);

        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        assertThat(productRepository.findById(501L)).map(Product::stock).contains(4); // 5 - 1

        PaymentHistoryRepository historyRepository = ctx.getBean(PaymentHistoryRepository.class);
        assertThat(historyRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(PaymentHistoryEntry::success);

        // published=true는 이 테스트가 Phase 3에서 처음 작성됐을 때는 없던 단언이다 - 그때는
        // Outbox "저장"까지만 있고 "발행"은 아직 없어서 여기서 항상 false였다. Phase 5가
        // OrderCompletedEvent에 대한 AFTER_COMMIT 리스너(OutboxPublishListener)를 추가한
        // 뒤로는, placeOrder()가 반환한 시점에 이미 커밋 직후 발행 시도까지 동기적으로 끝나
        // 있다 - 발행 세부 동작 자체는 OrderCompletionEventTest(Phase 5)가 다룬다.
        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(event -> event.eventType().equals("ORDER_PAID") && event.published());
    }

    @Test
    void aFailedPaymentRollsBackTheOrderAndTheStockButThePaymentHistoryRecordSurvives() {
        AnnotationConfigApplicationContext ctx = buildContext();
        // 가격이 0원인 상품 - PointPaymentGateway는 amountWon <= 0이면 예외 없이
        // success=false인 PaymentResult를 돌려준다("시스템 오류로 인한 실패"가 아니라
        // "정상적으로 거절된 결제"를 재현하기 위해 일부러 고른 경로다 - 예외가 던져지면
        // REQUIRES_NEW 트랜잭션 자체도 롤백되어 결제 이력이 남지 않는다).
        seedProduct(ctx, 502L, 0, 5);
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        assertThatThrownBy(() -> service.placeOrder(
                "cust-1", PaymentMethod.POINT, List.of(new OrderItemRequest(502L, 1))))
                .isInstanceOf(PaymentFailedException.class);

        long orderId = 1L; // 이 컨텍스트에서 발급된 첫 주문 ID

        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        assertThat(orderRepository.findById(orderId)).isEmpty(); // 주문 저장은 롤백됨

        OrderLineItemRepository lineItemRepository = ctx.getBean(OrderLineItemRepository.class);
        assertThat(lineItemRepository.findByOrderId(orderId)).isEmpty();

        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        assertThat(productRepository.findById(502L)).map(Product::stock).contains(5); // 차감도 롤백됨

        PaymentHistoryRepository historyRepository = ctx.getBean(PaymentHistoryRepository.class);
        assertThat(historyRepository.findByOrderId(orderId))
                .hasSize(1) // 하지만 REQUIRES_NEW로 독립 커밋된 결제 이력은 남아 있음
                .allMatch(entry -> !entry.success());

        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(orderId)).isEmpty(); // outbox도 주문과 같은 트랜잭션이라 롤백됨
    }

    @Test
    void cancellingAPendingOrderSucceeds() {
        AnnotationConfigApplicationContext ctx = buildContext();
        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        orderRepository.save(new Order(99L, "cust-1", 5_000, OrderStatus.PENDING));

        OrderCancellationService cancellationService = ctx.getBean(OrderCancellationService.class);
        CancellationOutcome outcome = cancellationService.cancelSwallowingValidationFailure(99L);

        assertThat(outcome).isEqualTo(CancellationOutcome.CANCELLED);
        assertThat(orderRepository.findById(99L)).map(Order::status).contains(OrderStatus.CANCELLED);
    }

    @Test
    void swallowingAnInnerRequiredValidationFailureStillMarksTheOuterTransactionRollbackOnly() {
        AnnotationConfigApplicationContext ctx = buildContext();
        seedProduct(ctx, 503L, 10_000, 5);
        OrderPlacementService placementService = ctx.getBean(OrderPlacementService.class);
        Order paidOrder = placementService.placeOrder(
                "cust-1", PaymentMethod.CARD, List.of(new OrderItemRequest(503L, 1)));

        OrderCancellationService cancellationService = ctx.getBean(OrderCancellationService.class);

        // cancelSwallowingValidationFailure()는 OrderNotCancellableException을 잡아서
        // CancellationOutcome을 정상 반환하려 하지만, orderValidator.assertCancellable()이
        // 같은(REQUIRED) 트랜잭션에 참여한 상태로 예외를 던졌기 때문에 그 트랜잭션은 이미
        // rollback-only로 표시돼 있다 - 애플리케이션이 예외를 삼켰다는 사실과 무관하게,
        // 메서드가 정상 반환되는 순간 커밋이 아니라 UnexpectedRollbackException으로 끝난다.
        assertThatThrownBy(() -> cancellationService.cancelSwallowingValidationFailure(paidOrder.id()))
                .isInstanceOf(UnexpectedRollbackException.class);

        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        assertThat(orderRepository.findById(paidOrder.id())).map(Order::status).contains(OrderStatus.PAID);
    }
}
