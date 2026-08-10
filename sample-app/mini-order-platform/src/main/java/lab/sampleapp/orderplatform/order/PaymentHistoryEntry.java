package lab.sampleapp.orderplatform.order;

import lab.sampleapp.orderplatform.payment.PaymentMethod;

public record PaymentHistoryEntry(
        long id, long orderId, PaymentMethod method, long amountWon, boolean success, String transactionId) {
}
