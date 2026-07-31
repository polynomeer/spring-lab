package lab.sampleapp.outbox;

public record Order(long id, String customerName, int amount) {
}
