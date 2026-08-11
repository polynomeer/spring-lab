package lab.sampleapp.orderplatform.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * 실제 메시지 브로커 대신 - 23주차 FakeMessageBroker와 같은 목적(발행을 흉내내고, 원하는
 * 시점에 실패를 주입할 수 있어야 한다)으로 이 모듈 안에 별도로 둔다. transactional-outbox-order
 * 모듈의 FakeMessageBroker를 직접 재사용하지 않은 이유: 그 모듈은 재사용 가능한 라이브러리로
 * 설계되지 않은 sample-app 리프 모듈이라, 모듈 간 의존을 새로 만드는 것보다 이 정도 크기의
 * 클래스는 그냥 다시 쓰는 편이 이 저장소의 모듈 경계 원칙에 더 맞는다.
 */
@Component
public class FakeOrderEventBroker implements OrderEventBroker {

    public record DeliveredMessage(long outboxEventId, String payload) {
    }

    private final List<DeliveredMessage> deliveredMessages = new CopyOnWriteArrayList<>();
    private boolean failNextSend;

    public void failNextSend() {
        this.failNextSend = true;
    }

    @Override
    public void send(long outboxEventId, String payload) {
        if (failNextSend) {
            failNextSend = false;
            throw new IllegalStateException("order event broker unavailable");
        }
        deliveredMessages.add(new DeliveredMessage(outboxEventId, payload));
    }

    public List<DeliveredMessage> deliveredMessages() {
        return List.copyOf(deliveredMessages);
    }
}
