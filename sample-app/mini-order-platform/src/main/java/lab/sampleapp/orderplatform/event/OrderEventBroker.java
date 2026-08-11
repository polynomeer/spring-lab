package lab.sampleapp.orderplatform.event;

public interface OrderEventBroker {

    void send(long outboxEventId, String payload);
}
