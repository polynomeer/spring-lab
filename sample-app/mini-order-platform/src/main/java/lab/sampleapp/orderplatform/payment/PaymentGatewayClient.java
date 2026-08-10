package lab.sampleapp.orderplatform.payment;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import lab.sampleapp.orderplatform.aop.TransientOperationException;

/**
 * 실제 결제사(PG) SDK를 흉내 낸 외부 클라이언트. 컨테이너가 이 빈을 초기화할 때
 * 핸드셰이크를 수행하고(@PostConstruct), 컨텍스트를 닫을 때 연결을 해제한다(@PreDestroy).
 * {@link CardPaymentGateway}가 이 클라이언트에 의존하므로, 초기화 순서상 이 빈이 먼저
 * connect()를 마쳐야 카드 결제가 가능하다.
 */
@Component
public class PaymentGatewayClient {

    private boolean connected;
    private int failNextPings;

    @PostConstruct
    void connect() {
        connected = true;
    }

    @PreDestroy
    void disconnect() {
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public String authorize(long amountWon) {
        if (!connected) {
            throw new IllegalStateException("PaymentGatewayClient is not connected");
        }
        return "PG-TXN-" + amountWon + "-" + System.nanoTime();
    }

    /** 다음 N번의 ping() 호출이 일시적으로 실패하도록 만든다(FakeMessageBroker#failNextSend와 같은 패턴). */
    public void failNextPings(int count) {
        this.failNextPings = count;
    }

    public void ping() {
        if (!connected) {
            throw new IllegalStateException("PaymentGatewayClient is not connected");
        }
        if (failNextPings > 0) {
            failNextPings--;
            throw new TransientOperationException("provider ping failed transiently");
        }
    }
}
