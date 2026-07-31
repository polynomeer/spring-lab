package lab.sampleapp.outbox;

public record OutboxEvent(long id, long orderId, String payload, boolean published) {
}
