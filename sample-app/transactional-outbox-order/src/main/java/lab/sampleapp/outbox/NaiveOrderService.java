package lab.sampleapp.outbox;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "주문 저장"과 "메시지 발행"을 서로 다른 두 시스템(DB, 메시지 브로커)에 대한 독립적인
// 쓰기(dual write)로 처리하는, 겉보기엔 자연스러워 보이는 접근이다 - NaiveOrderPlacedListener
// 참고, 원자성이 없다는 게 문제다.
@Service
public class NaiveOrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NaiveOrderService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void placeOrder(Order order) {
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlacedEvent(order.id()));
    }
}
