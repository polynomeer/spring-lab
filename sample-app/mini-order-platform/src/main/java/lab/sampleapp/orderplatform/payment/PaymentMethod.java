package lab.sampleapp.orderplatform.payment;

public enum PaymentMethod {
    CARD,
    POINT,
    // 의도적으로 아직 어떤 PaymentGateway도 구현하지 않은 값 - PaymentGatewayRegistry의
    // "등록되지 않은 결제 수단" 경계 조건을 테스트하기 위한 것.
    BANK_TRANSFER
}
