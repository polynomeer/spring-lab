package lab.sampleapp.orderplatform.event;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.notification.SentNotificationLog;
import lab.sampleapp.orderplatform.order.Order;
import lab.sampleapp.orderplatform.order.OrderItemRequest;
import lab.sampleapp.orderplatform.order.OrderOutboxRepository;
import lab.sampleapp.orderplatform.order.OrderPlacementService;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5(이벤트) 검증. @TransactionalEventListener(phase = AFTER_COMMIT)의 실제 실행
 * 시점을 코드로 재현한다 - 21주차 애플리케이션 이벤트 문서에서 이미 확인한 것("AFTER_COMMIT
 * 콜백은 트랜잭션 커밋 처리의 일부로, 호출자에게 제어가 돌아가기 전에 동기적으로 실행된다")을
 * 이번엔 Order 도메인 위에서 직접 확인한다.
 */
class OrderCompletionEventTest {

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
    void placingAnOrderDispatchesNotificationsAndPublishesTheOutboxEventAfterCommit() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ctx.getBean(ProductRepository.class).save(new Product(701L, "product-701", 10_000, 5));
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        // placeOrder()가 반환한 시점에는 이미 커밋과 그에 이은 AFTER_COMMIT 리스너들까지 전부
        // 동기적으로 끝나 있어야 한다 - 별도로 기다릴 필요가 없다.
        Order placed = service.placeOrder(
                "cust-1", PaymentMethod.CARD, List.of(new OrderItemRequest(701L, 1)));

        SentNotificationLog notificationLog = ctx.getBean(SentNotificationLog.class);
        assertThat(notificationLog.entries())
                .hasSize(2) // email + sms
                .allMatch(entry -> entry.memberId().equals("cust-1"))
                .allMatch(entry -> entry.message().contains("order " + placed.id()));

        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(placed.id()))
                .hasSize(1)
                .allMatch(event -> event.published());

        FakeOrderEventBroker broker = ctx.getBean(FakeOrderEventBroker.class);
        assertThat(broker.deliveredMessages()).hasSize(1);
    }

    @Test
    void aRolledBackTransactionNeverRunsTheAfterCommitListeners() {
        AnnotationConfigApplicationContext ctx = buildContext();
        // AnnotationConfigApplicationContext 자신이 ApplicationEventPublisher를 구현한다 -
        // 컨테이너가 별도 빈으로 등록해 두는 게 아니라 컨텍스트 자체의 역할이라, getBean()으로
        // 찾을 수 없다(직접 겪은 것 - 처음엔 getBean(ApplicationEventPublisher.class)를
        // 시도했다가 NoSuchBeanDefinitionException을 봤다).
        ApplicationEventPublisher eventPublisher = ctx;
        PlatformTransactionManager transactionManager = ctx.getBean(PlatformTransactionManager.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new OrderCompletedEvent(999L, "cust-2", 1_000));
            throw new RuntimeException("force rollback after publishing");
        })).isInstanceOf(RuntimeException.class);

        SentNotificationLog notificationLog = ctx.getBean(SentNotificationLog.class);
        assertThat(notificationLog.entries()).isEmpty();

        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(999L)).isEmpty();
    }

    @Test
    void aBrokerFailureLeavesTheOutboxEventUnpublishedForTheNextPoll() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ctx.getBean(ProductRepository.class).save(new Product(702L, "product-702", 10_000, 5));
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);
        FakeOrderEventBroker broker = ctx.getBean(FakeOrderEventBroker.class);

        broker.failNextSend();
        Order placed = service.placeOrder(
                "cust-1", PaymentMethod.CARD, List.of(new OrderItemRequest(702L, 1)));

        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(placed.id())).allMatch(event -> !event.published());

        // 다음 폴링(수동으로 재현) - 브로커가 이번엔 정상이라 성공한다.
        OrderOutboxPublisher outboxPublisher = ctx.getBean(OrderOutboxPublisher.class);
        outboxPublisher.publishPending();

        assertThat(outboxRepository.findByOrderId(placed.id())).allMatch(event -> event.published());
    }
}
