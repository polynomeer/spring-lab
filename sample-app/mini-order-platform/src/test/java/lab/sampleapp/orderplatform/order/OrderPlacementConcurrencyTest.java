package lab.sampleapp.orderplatform.order;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.product.InsufficientStockException;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductRepositoryConcurrencyTest}가 저장소 계층 하나만 떼어서 확인한 원자성이,
 * 실제로 쓰이는 경로인 {@link OrderPlacementService#placeOrder}를 통해서도 그대로
 * 유지되는지 확인한다 - 결제(Phase 1/2)/트랜잭션(Phase 3)/이벤트(Phase 5) 전부가 얽힌
 * 실제 호출 경로 안에서, 두 "고객"이 재고 1개짜리 상품을 동시에 주문하면 정확히 한 명만
 * 성공해야 한다.
 *
 * <p>{@link CurrentActor}는 ThreadLocal이라 이 테스트를 실행하는 메인 스레드에서
 * set()해 봐야 실행기(executor)의 작업 스레드에는 전혀 보이지 않는다 - 그래서 각 작업
 * 스레드가 자기 자신의 액터를 직접 set()한다(Phase 4의 CurrentMemberArgumentResolver가
 * 실제 서블릿 요청 스레드마다 하는 일과 같다).
 */
class OrderPlacementConcurrencyTest {

    private AnnotationConfigApplicationContext context;

    private AnnotationConfigApplicationContext buildContext() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        return context;
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void twoCustomersRacingForTheLastUnitOnlyOneOrderSucceeds() throws Exception {
        AnnotationConfigApplicationContext ctx = buildContext();
        long productId = 401L;
        ctx.getBean(ProductRepository.class).save(new Product(productId, "last-unit-item", 10_000, 1));

        OrderPlacementService service = ctx.getBean(OrderPlacementService.class);
        CurrentActor currentActor = ctx.getBean(CurrentActor.class);

        List<String> customerIds = List.of("cust-A", "cust-B");
        ExecutorService executor = Executors.newFixedThreadPool(customerIds.size());
        CountDownLatch ready = new CountDownLatch(customerIds.size());
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Order>> tasks = customerIds.stream()
                .<Callable<Order>>map(customerId -> () -> {
                    currentActor.set(new CurrentActor.Actor(customerId, Role.CUSTOMER));
                    ready.countDown();
                    start.await();
                    return service.placeOrder(
                            customerId, PaymentMethod.CARD, List.of(new OrderItemRequest(productId, 1)));
                })
                .toList();

        List<Future<Order>> futures = tasks.stream().map(executor::submit).toList();

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        int succeeded = 0;
        int rejected = 0;
        Order winningOrder = null;
        for (Future<Order> future : futures) {
            try {
                winningOrder = future.get(5, TimeUnit.SECONDS);
                succeeded++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientStockException.class);
                rejected++;
            }
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(winningOrder).isNotNull();
        assertThat(winningOrder.status()).isEqualTo(OrderStatus.PAID);
        assertThat(customerIds).contains(winningOrder.memberId());

        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        assertThat(productRepository.findById(productId)).map(Product::stock).contains(0);

        // 진 쪽은 InsufficientStockException으로 트랜잭션 전체가 롤백됐으므로, 주문 테이블에
        // 흔적조차 남지 않는다 - 이긴 쪽의 주문 하나만 존재해야 한다.
        OrderOutboxRepository outboxRepository = ctx.getBean(OrderOutboxRepository.class);
        assertThat(outboxRepository.findByOrderId(winningOrder.id())).hasSize(1);
    }
}
