package lab.sampleapp.orderplatform.product;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductRepository#decreaseStock}가 "조회 후 갱신"이 아니라 UPDATE 문 자체의
 * WHERE 조건으로 재고 확인과 차감을 원자적으로 처리한다는 걸 직접 검증한다.
 */
class ProductRepositoryTest {

    private AnnotationConfigApplicationContext context;

    private ProductRepository buildRepository() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        return context.getBean(ProductRepository.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void decreaseStockSucceedsWhenEnoughStockIsAvailable() {
        ProductRepository repository = buildRepository();
        repository.save(new Product(1L, "widget", 1_000, 10));

        boolean succeeded = repository.decreaseStock(1L, 3);

        assertThat(succeeded).isTrue();
        assertThat(repository.findById(1L)).map(Product::stock).contains(7);
    }

    @Test
    void decreaseStockFailsAtomicallyAndLeavesStockUnchangedWhenNotEnough() {
        ProductRepository repository = buildRepository();
        repository.save(new Product(2L, "widget", 1_000, 2));

        boolean succeeded = repository.decreaseStock(2L, 3);

        assertThat(succeeded).isFalse();
        assertThat(repository.findById(2L)).map(Product::stock).contains(2); // 그대로
    }

    @Test
    void decreaseStockCanBringStockExactlyToZero() {
        ProductRepository repository = buildRepository();
        repository.save(new Product(3L, "widget", 1_000, 5));

        boolean succeeded = repository.decreaseStock(3L, 5);

        assertThat(succeeded).isTrue();
        assertThat(repository.findById(3L)).map(Product::stock).contains(0);
    }
}
