package lab.experiments.validation;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodValidationTest {

    private AnnotationConfigApplicationContext context;
    private OrderService orderService;
    private UnvalidatedOrderService unvalidatedOrderService;
    private InvocationCounter counter;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(ValidationPlaygroundConfig.class);
        orderService = context.getBean(OrderService.class);
        unvalidatedOrderService = context.getBean(UnvalidatedOrderService.class);
        counter = context.getBean(InvocationCounter.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void validParametersInvokeTheTargetNormally() {
        orderService.placeOrder("c1", 3);

        assertThat(counter.count("placeOrder")).isEqualTo(1);
    }

    @Test
    void blankCustomerIdIsRejectedBeforeTheTargetRuns() {
        assertThatThrownBy(() -> orderService.placeOrder("", 3))
                .isInstanceOf(ConstraintViolationException.class);

        // 위반이 있으면 MethodValidationInterceptor#invoke()는 invocation.proceed()를
        // 아예 호출하지 않는다 - 파라미터 검증은 대상 메서드 실행 "전"에 일어난다.
        assertThat(counter.count("placeOrder")).isEqualTo(0);
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        ConstraintViolationException ex = catchConstraintViolation(() -> orderService.placeOrder("c1", 0));

        assertThat(ex.getConstraintViolations()).hasSize(1);
        assertThat(counter.count("placeOrder")).isEqualTo(0);
    }

    @Test
    void cascadingAtValidOnANestedParameterCatchesItsOwnFieldViolations() {
        OrderRequest invalid = new OrderRequest("", 1, null);

        assertThatThrownBy(() -> orderService.placeValidatedOrder(invalid))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(counter.count("placeValidatedOrder")).isEqualTo(0);
    }

    @Test
    void validNestedRequestInvokesTheTargetNormally() {
        OrderRequest valid = new OrderRequest("c1", 2, null);

        orderService.placeValidatedOrder(valid);

        assertThat(counter.count("placeValidatedOrder")).isEqualTo(1);
    }

    @Test
    void defaultGroupIgnoresAConstraintScopedToAnotherGroup() {
        // courier는 @NotBlank(groups = ExpressGroup.class)라서 기본(Default) 그룹에서는
        // 아예 검사되지 않는다 - null이어도 통과한다.
        OrderRequest withoutCourier = new OrderRequest("c1", 1, null);

        orderService.placeValidatedOrder(withoutCourier);

        assertThat(counter.count("placeValidatedOrder")).isEqualTo(1);
    }

    @Test
    void methodLevelValidatedGroupEnforcesTheGroupScopedConstraint() {
        // placeExpressOrder()에 붙은 @Validated(ExpressGroup.class)가 클래스 레벨 기본
        // 그룹을 이 메서드에 한해서만 ExpressGroup으로 바꾼다 - 같은 courier=null 요청이
        // 이번엔 거부된다.
        OrderRequest withoutCourier = new OrderRequest("c1", 1, null);

        assertThatThrownBy(() -> orderService.placeExpressOrder(withoutCourier))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(counter.count("placeExpressOrder")).isEqualTo(0);
    }

    @Test
    void nullReturnValueViolatesTheMethodLevelNotNullConstraint() {
        // 반환값 검증은 대상 메서드가 실제로 실행된 "뒤"에 일어난다 - 파라미터 검증과 달리
        // 카운터는 이미 증가해 있다.
        assertThatThrownBy(() -> orderService.findCustomerName("missing"))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(counter.count("findCustomerName")).isEqualTo(1);
    }

    @Test
    void nonNullReturnValuePassesThrough() {
        String name = orderService.findCustomerName("c1");

        assertThat(name).isEqualTo("customer-c1");
    }

    @Test
    void selfInvocationBypassesParameterValidationEntirely() {
        // customerId=""와 quantity=-1은 둘 다 명백한 위반이지만, refreshViaSelfInvocation()
        // 내부의 this.placeOrder(...) 호출은 프록시를 거치지 않으므로 예외 없이 통과한다.
        orderService.refreshViaSelfInvocation("", -1);

        assertThat(counter.count("placeOrder")).isEqualTo(1);
    }

    @Test
    void constraintAnnotationsAreInertWithoutTheValidatedAnnotationOnTheImplementationClass() {
        // UnvalidatedOrderServiceImpl에는 @Validated가 없다 - MethodValidationPostProcessor의
        // 포인트컷이 아예 이 빈을 대상으로 고르지 않으므로 프록시 자체가 생기지 않는다.
        // @NotBlank/@Min 애노테이션은 그냥 읽히지 않는 메타데이터로 남는다.
        unvalidatedOrderService.placeOrder("", -5);

        assertThat(counter.count("unvalidated.placeOrder")).isEqualTo(1);
    }

    private ConstraintViolationException catchConstraintViolation(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected a ConstraintViolationException");
        } catch (ConstraintViolationException ex) {
            return ex;
        }
    }
}
