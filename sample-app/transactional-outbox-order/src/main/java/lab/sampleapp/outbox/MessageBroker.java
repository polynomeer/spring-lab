package lab.sampleapp.outbox;

public interface MessageBroker {

    void send(long messageId, String payload);
}
