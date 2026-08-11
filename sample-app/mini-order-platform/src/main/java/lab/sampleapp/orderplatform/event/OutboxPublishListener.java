package lab.sampleapp.orderplatform.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 직후 최선을 다해(best-effort) 한 번 발행을 시도한다 - 이게 실패해도 아웃박스 행 자체는
 * published=false로 안전하게 남아 있으므로 유실되지 않는다(23주차와 같은 보장). 실제
 * 운영에서는 이 즉시 시도 외에 별도 스케줄러가 주기적으로 publishPending()을 다시 호출해서
 * "즉시 시도가 실패한 경우"까지 복구하지만, 이 캡스톤은 23주차와 같은 이유로 @Scheduled
 * 폴링까지는 범위에 넣지 않았다 - 폴링 주기/백오프는 이 프로젝트의 핵심 질문(원자성과
 * 커밋 이후 처리)과는 다른 운영 관심사라고 이미 그 문서에서 정리했다.
 */
@Component
public class OutboxPublishListener {

    private final OrderOutboxPublisher outboxPublisher;

    public OutboxPublishListener(OrderOutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        outboxPublisher.publishPending();
    }
}
