package lab.sampleapp.orderplatform.product;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.AccessDeniedException;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 등록에 Phase 2(AOP)의 @RequiresRole(ADMIN)을 그대로 재사용한 게 실제로 새 도메인에서도
 * 동작하는지 확인한다 - PaymentProcessingService.refund()와 정확히 같은 검증 방식이다.
 */
class ProductServiceTest {

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
    void creatingAProductAsCustomerIsDenied() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ctx.getBean(CurrentActor.class).set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));
        ProductService productService = ctx.getBean(ProductService.class);

        assertThatThrownBy(() -> productService.createProduct("widget", 1_000, 10))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(ctx.getBean(ProductRepository.class).findAll()).isEmpty();
    }

    @Test
    void creatingAProductAsAdminSucceedsAndPersists() {
        AnnotationConfigApplicationContext ctx = buildContext();
        ctx.getBean(CurrentActor.class).set(new CurrentActor.Actor("admin-1", Role.ADMIN));
        ProductService productService = ctx.getBean(ProductService.class);

        Product created = productService.createProduct("widget", 1_000, 10);

        assertThat(ctx.getBean(ProductRepository.class).findById(created.id())).contains(created);
    }
}
