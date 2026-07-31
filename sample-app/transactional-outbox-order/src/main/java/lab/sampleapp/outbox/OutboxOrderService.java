package lab.sampleapp.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주문과 아웃박스 이벤트를 "같은 DB, 같은 트랜잭션"에 함께 쓴다 - 서로 다른 두 시스템에
// 걸친 쓰기가 아니라 하나의 로컬 트랜잭션이므로, 커밋되면 둘 다 남고 롤백되면 둘 다 없다.
// 메시지 브로커로의 실제 전송은 이 트랜잭션 밖, OutboxPublisher가 별도로 담당한다.
@Service
public class OutboxOrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    public OutboxOrderService(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void placeOrder(Order order, long outboxEventId) {
        orderRepository.save(order);
        outboxRepository.save(new OutboxEvent(outboxEventId, order.id(), "order-placed:" + order.id(), false));
    }
}
