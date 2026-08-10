package lab.sampleapp.orderplatform.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional의 propagation 기본값은 REQUIRED다 - 별도 빈이지만, 이미 진행 중인 호출자의
 * 트랜잭션이 있으면 새로 시작하지 않고 그 트랜잭션에 참여한다. 그래서 이 메서드가 던지는
 * 예외는 "이 메서드만의" 트랜잭션이 아니라 호출자의 트랜잭션 전체를 rollback-only로
 * 표시한다 - {@link OrderCancellationService}가 이 사실을 직접 재현한다.
 */
@Component
public class OrderValidator {

    private final OrderRepository orderRepository;

    public OrderValidator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void assertCancellable(long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.status() == OrderStatus.PAID) {
            throw new OrderNotCancellableException("order " + orderId + " is already PAID, cannot cancel");
        }
    }
}
