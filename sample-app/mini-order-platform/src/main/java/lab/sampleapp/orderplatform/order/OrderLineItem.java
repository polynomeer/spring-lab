package lab.sampleapp.orderplatform.order;

public record OrderLineItem(long id, long orderId, long productId, int quantity, long unitPriceWon) {
}
