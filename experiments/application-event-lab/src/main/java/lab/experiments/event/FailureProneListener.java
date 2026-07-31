package lab.experiments.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// SimpleApplicationEventMulticaster는 errorHandler가 없으면 리스너 예외를 그대로 던지고,
// multicastEvent()의 for 루프도 그 자리에서 멈춘다 - 순서상 이후 리스너는 아예 호출되지 않는다.
@Component
public class FailureProneListener {

    private final EventLog eventLog;

    public FailureProneListener(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    @Order(1)
    @EventListener
    public void explodesOnNegativeId(OrderCompletedEvent event) {
        if (event.orderId() < 0) {
            throw new IllegalStateException("negative order id: " + event.orderId());
        }
        eventLog.record("failure-prone-first");
    }

    @Order(40)
    @EventListener
    public void neverReachedIfAnEarlierListenerThrew(OrderCompletedEvent event) {
        eventLog.record("failure-prone-last");
    }
}
