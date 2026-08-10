package lab.sampleapp.orderplatform.order;

public record Order(long id, String memberId, long amountWon, OrderStatus status) {
}
