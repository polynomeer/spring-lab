package lab.sampleapp.orderplatform.payment;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lab.sampleapp.orderplatform.aop.TransientOperationException;

/**
 * 실제 결제사(PG) SDK를 흉내 낸 외부 클라이언트. 컨테이너가 이 빈을 초기화할 때
 * 핸드셰이크를 수행하고(@PostConstruct), 컨텍스트를 닫을 때 연결을 해제한다(@PreDestroy).
 * {@link CardPaymentGateway}가 이 클라이언트에 의존하므로, 초기화 순서상 이 빈이 먼저
 * connect()를 마쳐야 카드 결제가 가능하다.
 *
 * <p>Phase 1에서는 여기 {@code @Component}가 붙어 있었다 - Phase 6에서
 * {@code lab.sampleapp.orderplatform.boot.PaymentGatewayAutoConfiguration}으로 등록
 * 방식을 옮기면서 뗐다. @PostConstruct/@PreDestroy 콜백은 등록 방식과 무관하게 여전히
 * 호출된다(CommonAnnotationBeanPostProcessor는 빈이 컴포넌트 스캔으로 왔는지 @Bean
 * 메서드로 왔는지 신경 쓰지 않는다 - Phase 1의 PluginRegistrationBeanPostProcessor에서
 * 이미 확인한 것과 같은 성질).
 */
public class PaymentGatewayClient {

    private boolean connected;
    private int failNextPings;
    private final int connectTimeoutSeconds;

    public PaymentGatewayClient() {
        this(5);
    }

    public PaymentGatewayClient(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int connectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

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
