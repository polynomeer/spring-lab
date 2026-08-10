package lab.sampleapp.orderplatform.payment;

public record PaymentResult(boolean success, String transactionId, String message) {
}
