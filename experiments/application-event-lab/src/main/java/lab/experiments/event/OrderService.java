package lab.experiments.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void completeOrder(long orderId) {
        eventPublisher.publishEvent(new OrderCompletedEvent(orderId));
    }

    @Transactional
    public void completeOrderInTransaction(long orderId, boolean forceRollback) {
        eventPublisher.publishEvent(new OrderCompletedEvent(orderId));
        if (forceRollback) {
            throw new IllegalStateException("forcing rollback to check that AFTER_COMMIT does not fire");
        }
    }
}
