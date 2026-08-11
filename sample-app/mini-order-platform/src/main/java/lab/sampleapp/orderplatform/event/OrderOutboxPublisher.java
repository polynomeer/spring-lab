package lab.sampleapp.orderplatform.event;

import org.springframework.stereotype.Component;

import lab.sampleapp.orderplatform.order.OrderOutboxEvent;
import lab.sampleapp.orderplatform.order.OrderOutboxRepository;

/**
 * 23주차 아웃박스 패턴의 발행 단계(OutboxPublisher.publishPending())와 같은 모양이다:
 * 미발행 행을 찾아 브로커로 보내고, 성공하면 published로 표시한다. 실패하면 그 행은
 * published=false로 그대로 남아 다음 호출에서 다시 시도된다("최소 한 번" 전달) - 이
 * 클래스 자체는 트랜잭션 경계를 새로 열지 않는다(각 markPublished()가 독자적인 auto-commit
 * 성격의 짧은 갱신일 뿐이라, 여러 행을 처리하는 도중 실패해도 이미 처리한 행까지는 그대로
 * published로 남는다).
 */
@Component
public class OrderOutboxPublisher {

    private final OrderOutboxRepository outboxRepository;
    private final OrderEventBroker broker;

    public OrderOutboxPublisher(OrderOutboxRepository outboxRepository, OrderEventBroker broker) {
        this.outboxRepository = outboxRepository;
        this.broker = broker;
    }

    public void publishPending() {
        for (OrderOutboxEvent event : outboxRepository.findUnpublished()) {
            try {
                broker.send(event.id(), event.payload());
                outboxRepository.markPublished(event.id());
            } catch (RuntimeException ex) {
                // 이 행은 published=false로 남는다 - 다음 publishPending() 호출(다음 폴링
                // 주기)이 자동으로 재시도한다. 나머지 미발행 행은 계속 처리한다.
            }
        }
    }
}
