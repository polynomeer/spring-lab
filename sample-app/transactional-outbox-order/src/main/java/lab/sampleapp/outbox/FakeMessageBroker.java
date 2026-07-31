package lab.sampleapp.outbox;

import java.util.ArrayList;
import java.util.List;

// 실제 브로커(Kafka/RabbitMQ 등) 대신 - "발행"을 흉내내는 인메모리 구현이다. 실패를 인위적으로
// 주입할 수 있어야 dual-write 문제와 outbox의 복구 가능성을 재현할 수 있다.
public class FakeMessageBroker implements MessageBroker {

    public record DeliveredMessage(long messageId, String payload) {
    }

    private final List<DeliveredMessage> sentMessages = new ArrayList<>();
    private boolean failNextSend;

    public void failNextSend() {
        this.failNextSend = true;
    }

    @Override
    public void send(long messageId, String payload) {
        if (failNextSend) {
            failNextSend = false;
            throw new IllegalStateException("broker unavailable");
        }
        sentMessages.add(new DeliveredMessage(messageId, payload));
    }

    public List<DeliveredMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    // at-least-once 브로커를 흉내낸다 - 재전송으로 같은 메시지가 두 번 배달될 수 있다.
    public void redeliver(long messageId) {
        sentMessages().stream()
                .filter(message -> message.messageId() == messageId)
                .findFirst()
                .ifPresent(sentMessages::add);
    }
}
