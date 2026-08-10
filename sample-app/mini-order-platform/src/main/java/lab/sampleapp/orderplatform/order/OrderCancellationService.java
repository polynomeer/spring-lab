package lab.sampleapp.orderplatform.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "rollback-only" 실험 - orderValidator.assertCancellable()가 던진 예외를 여기서 잡아
 * 삼키면(그리고 정상적으로 return하면) 호출한 쪽에서는 마치 아무 일도 없었던 것처럼 보인다.
 * 하지만 assertCancellable()이 REQUIRED로 이 메서드와 같은 트랜잭션에 참여했기 때문에,
 * 그 예외가 발생한 순간 AbstractPlatformTransactionManager가 이미 현재 트랜잭션을
 * rollback-only로 표시해 버렸다 - 애플리케이션 코드가 예외를 삼켰다는 사실과 무관하게,
 * 이 메서드가 정상적으로 반환된 뒤 @Transactional 어드바이스가 커밋을 시도하면
 * UnexpectedRollbackException이 던져진다.
 */
@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;

    public OrderCancellationService(OrderRepository orderRepository, OrderValidator orderValidator) {
        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
    }

    @Transactional
    public CancellationOutcome cancelSwallowingValidationFailure(long orderId) {
        try {
            orderValidator.assertCancellable(orderId);
        } catch (OrderNotCancellableException ex) {
            return CancellationOutcome.VALIDATION_FAILED_BUT_SWALLOWED;
        }
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
        return CancellationOutcome.CANCELLED;
    }
}
