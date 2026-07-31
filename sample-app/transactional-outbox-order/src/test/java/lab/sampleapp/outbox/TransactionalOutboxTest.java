package lab.sampleapp.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalOutboxTest {

    private AnnotationConfigApplicationContext context;
    private FakeMessageBroker broker;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(OutboxSampleConfig.class);
        broker = context.getBean(FakeMessageBroker.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void naiveDualWriteLosesTheMessageWhenTheBrokerFailsAfterCommit() {
        NaiveOrderService naiveOrderService = context.getBean(NaiveOrderService.class);
        OrderRepository orderRepository = context.getBean(OrderRepository.class);
        broker.failNextSend();

        // AFTER_COMMIT 리스너 안에서 브로커 전송이 실패해도, DB 커밋 자체는 이미 끝난 뒤라
        // 이 실패가 주문 저장을 되돌리지 못한다.
        naiveOrderService.placeOrder(new Order(1L, "alice", 1000));

        assertThat(orderRepository.existsById(1L)).isTrue();
        assertThat(broker.sentMessages()).isEmpty();
        // 이 시점에서 "주문 1의 이벤트를 다시 보내라"고 할 방법이 전혀 없다 - durable한 기록이
        // 없으므로 유실은 영구적이다.
    }

    @Test
    void outboxRecoversFromAPublishFailureOnTheNextPoll() {
        OutboxOrderService outboxOrderService = context.getBean(OutboxOrderService.class);
        OutboxRepository outboxRepository = context.getBean(OutboxRepository.class);
        OutboxPublisher outboxPublisher = context.getBean(OutboxPublisher.class);

        outboxOrderService.placeOrder(new Order(2L, "bob", 2000), 100L);

        broker.failNextSend();
        outboxPublisher.publishPending();
        assertThat(outboxRepository.isPublished(100L)).isFalse();
        assertThat(broker.sentMessages()).isEmpty();

        // 다음 폴링 - outbox_events에 published=false로 그대로 남아 있었기 때문에 재시도할 수
        // 있다. 이것이 outbox 패턴이 "발행 실패가 곧 유실은 아니다"라고 주장하는 지점이다.
        outboxPublisher.publishPending();
        assertThat(outboxRepository.isPublished(100L)).isTrue();
        assertThat(broker.sentMessages())
                .extracting(FakeMessageBroker.DeliveredMessage::messageId)
                .contains(100L);
    }

    @Test
    void consumerIdempotencyPreventsDoubleProcessingOnRedelivery() {
        OutboxOrderService outboxOrderService = context.getBean(OutboxOrderService.class);
        OutboxPublisher outboxPublisher = context.getBean(OutboxPublisher.class);
        ProcessedMessageRepository processedMessageRepository = context.getBean(ProcessedMessageRepository.class);

        outboxOrderService.placeOrder(new Order(3L, "carol", 3000), 200L);
        outboxPublisher.publishPending();

        // at-least-once 브로커를 흉내낸 재전송 - 같은 메시지가 두 번 배달된다.
        broker.redeliver(200L);
        assertThat(broker.sentMessages()).hasSize(2);

        int processedCount = 0;
        for (FakeMessageBroker.DeliveredMessage message : broker.sentMessages()) {
            if (processedMessageRepository.markProcessedIfNew(message.messageId())) {
                processedCount++;
            }
        }

        // 메시지 ID 기준으로 durable하게 기록해 뒀기 때문에, 두 번 배달돼도 실제 처리는 한 번뿐이다.
        assertThat(processedCount).isEqualTo(1);
    }
}
