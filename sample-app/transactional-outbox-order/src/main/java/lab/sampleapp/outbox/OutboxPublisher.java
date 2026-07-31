package lab.sampleapp.outbox;

import java.util.List;

import org.springframework.stereotype.Component;

// 실제로는 별도 스케줄러/폴러가 주기적으로 호출한다 - 여기서는 테스트가 직접 호출해서 "한 번의
// 폴링 주기"를 흉내낸다. 발행에 실패한 이벤트는 published=false로 그대로 남으므로 다음 호출에서
// 다시 시도된다 - outbox 패턴이 "실패해도 유실되지 않는다"고 주장하는 지점이 바로 여기다.
@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final MessageBroker messageBroker;

    public OutboxPublisher(OutboxRepository outboxRepository, MessageBroker messageBroker) {
        this.outboxRepository = outboxRepository;
        this.messageBroker = messageBroker;
    }

    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findUnpublished();
        for (OutboxEvent event : pending) {
            try {
                messageBroker.send(event.id(), event.payload());
                outboxRepository.markPublished(event.id());
            } catch (RuntimeException ex) {
                // 이 이벤트는 published=false로 그대로 남는다 - 다음 폴링에서 재시도된다.
                // (실제라면 여기서 로그를 남기고 다음 이벤트로 넘어간다.)
            }
        }
    }
}
