package lab.sampleapp.orderplatform.order;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.product.InsufficientStockException;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductNotFoundException;
import lab.sampleapp.orderplatform.product.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카탈로그(32개 프로젝트 + Mini Order Platform 6개 Phase) 완료 이후 추가한 상품 도메인
 * 검증. 클라이언트는 상품 ID와 수량만 보내고, 가격/합계는 서버가 Product 테이블에서
 * 계산한다 - {@link OrderPlacementService}의 클래스 주석에 적어 둔 설계 이유를 실제로
 * 확인한다.
 */
class OrderPlacementWithProductsTest {

    private AnnotationConfigApplicationContext context;

    private AnnotationConfigApplicationContext buildContext() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        context.getBean(CurrentActor.class).set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));
        return context;
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void placingAnOrderWithMultipleLineItemsComputesTheTotalFromProductPrices() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        productRepository.save(new Product(11L, "widget", 3_000, 10));
        productRepository.save(new Product(12L, "gadget", 5_000, 10));
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        Order placed = service.placeOrder("cust-1", PaymentMethod.CARD, List.of(
                new OrderItemRequest(11L, 2), // 3,000 * 2 = 6,000
                new OrderItemRequest(12L, 1))); // 5,000 * 1 = 5,000

        assertThat(placed.amountWon()).isEqualTo(11_000);

        OrderLineItemRepository lineItemRepository = ctx.getBean(OrderLineItemRepository.class);
        assertThat(lineItemRepository.findByOrderId(placed.id()))
                .hasSize(2)
                .extracting(OrderLineItem::productId, OrderLineItem::quantity, OrderLineItem::unitPriceWon)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(11L, 2, 3_000L),
                        org.assertj.core.groups.Tuple.tuple(12L, 1, 5_000L));

        assertThat(productRepository.findById(11L)).map(Product::stock).contains(8);
        assertThat(productRepository.findById(12L)).map(Product::stock).contains(9);
    }

    @Test
    void insufficientStockForOneItemRollsBackTheWholeOrderAndRestoresStockAlreadyDecremented() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        productRepository.save(new Product(21L, "widget", 3_000, 10)); // 충분
        productRepository.save(new Product(22L, "gadget", 5_000, 1)); // 부족할 예정
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        assertThatThrownBy(() -> service.placeOrder("cust-1", PaymentMethod.CARD, List.of(
                new OrderItemRequest(21L, 2), // 먼저 처리되며 재고 8로 차감됨
                new OrderItemRequest(22L, 5)))) // 재고 1뿐이라 여기서 실패
                .isInstanceOf(InsufficientStockException.class);

        // 트랜잭션 전체가 롤백됐으므로, 먼저 처리돼 이미 차감됐던 21번 상품의 재고도 원래대로다.
        assertThat(productRepository.findById(21L)).map(Product::stock).contains(10);
        assertThat(productRepository.findById(22L)).map(Product::stock).contains(1);

        long orderId = 1L; // 이 컨텍스트에서 발급된 첫 주문 ID
        assertThat(ctx.getBean(OrderRepository.class).findById(orderId)).isEmpty();
        assertThat(ctx.getBean(OrderLineItemRepository.class).findByOrderId(orderId)).isEmpty();
    }

    @Test
    void orderingAnUnknownProductThrowsBeforeAnyStockIsTouched() {
        AnnotationConfigApplicationContext ctx = buildContext();
        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);

        assertThatThrownBy(() -> service.placeOrder(
                "cust-1", PaymentMethod.CARD, List.of(new OrderItemRequest(999L, 1))))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
