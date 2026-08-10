package lab.sampleapp.orderplatform.order;

public record OrderOutboxEvent(long id, long orderId, String eventType, String payload, boolean published) {
}
