package lab.sampleapp.orderplatform.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.UnexpectedRollbackException;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.payment.PaymentMethod;

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

    @Test
    void placingAnOrderWithSuccessfulPaymentCommitsOrderPaymentHistoryAndOutboxTogether() {
        AnnotationConfigApplicationContext ctx = buildContext();
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        Order placed = service.placeOrder("cust-1", PaymentMethod.CARD, 10_000);

        assertThat(placed.status()).isEqualTo(OrderStatus.PAID);

        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        assertThat(orderRepository.findById(placed.id())).contains(placed);

        PaymentHistoryRepository historyRepository = ctx.getBean(PaymentHistoryRepository.class);
        assertThat(historyRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(PaymentHistoryEntry::success);

        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(event -> event.eventType().equals("ORDER_PAID") && !event.published());
    }

    @Test
    void aFailedPaymentRollsBackTheOrderButThePaymentHistoryRecordSurvives() {
        AnnotationConfigApplicationContext ctx = buildContext();
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        // PointPaymentGateway는 amountWon <= 0이면 예외 없이 success=false인 PaymentResult를
        // 돌려준다 - "시스템 오류로 인한 실패"가 아니라 "정상적으로 거절된 결제"를 재현하기 위해
        // 일부러 고른 경로다(예외가 던져지면 REQUIRES_NEW 트랜잭션 자체도 롤백되어 결제 이력이
        // 남지 않는다).
        assertThatThrownBy(() -> service.placeOrder("cust-1", PaymentMethod.POINT, 0))
                .isInstanceOf(PaymentFailedException.class);

        long orderId = 1L; // 이 컨텍스트에서 발급된 첫 주문 ID

        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        assertThat(orderRepository.findById(orderId)).isEmpty(); // 주문 저장은 롤백됨

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
        OrderPlacementService placementService = ctx.getBean(OrderPlacementService.class);
        Order paidOrder = placementService.placeOrder("cust-1", PaymentMethod.CARD, 10_000);

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
