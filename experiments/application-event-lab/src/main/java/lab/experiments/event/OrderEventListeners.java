package lab.experiments.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventListeners {

    private final EventLog eventLog;

    public OrderEventListeners(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    @Order(10)
    @EventListener
    public void firstSyncListener(OrderCompletedEvent event) {
        eventLog.record("sync-first");
    }

    @Order(20)
    @EventListener
    public void secondSyncListener(OrderCompletedEvent event) {
        eventLog.record("sync-second");
    }

    // #event는 이 메서드 파라미터 이름 그대로 SpEL에 바인딩된다(MethodBasedEvaluationContext,
    // -parameters 컴파일 옵션이 루트 build.gradle.kts에서 전역으로 켜져 있어 가능하다).
    @Order(30)
    @EventListener(condition = "#event.orderId() % 2 == 0")
    public void evenOrderIdOnlyListener(OrderCompletedEvent event) {
        eventLog.record("condition-even");
    }

    @Async
    @EventListener
    public void asyncListener(OrderCompletedEvent event) {
        eventLog.record("async");
    }

    // phase 기본값이 이미 AFTER_COMMIT이다(TransactionalEventListener#phase() 기본값 확인됨).
    @TransactionalEventListener
    public void afterCommitListener(OrderCompletedEvent event) {
        eventLog.record("after-commit");
    }
}
