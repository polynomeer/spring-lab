package lab.sampleapp.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NaiveOrderPlacedListener {

    private final MessageBroker messageBroker;

    public NaiveOrderPlacedListener(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    // AFTER_COMMIT이므로 DB 커밋 자체는 이미 성공한 뒤다 - 그런데 이 브로커 전송이 실패하면,
    // 그 사실을 durable하게 기록해 둘 곳이 이 접근에는 전혀 없다. 재시도할 방법이 없다
    // (21주차 문서의 @TransactionalEventListener 학습과 이어지는 지점 - "커밋 후 실행"이
    // "실행이 보장된다"는 뜻은 아니다).
    @TransactionalEventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        messageBroker.send(event.orderId(), "order-placed:" + event.orderId());
    }
}
