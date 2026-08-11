package lab.sampleapp.orderplatform.order;

/** 클라이언트는 상품 ID와 수량만 보낸다 - 단가/합계는 서버가 Product 테이블에서 직접 계산한다. */
public record OrderItemRequest(long productId, int quantity) {
}
