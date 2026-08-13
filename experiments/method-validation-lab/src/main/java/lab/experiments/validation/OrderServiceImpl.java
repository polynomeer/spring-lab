package lab.experiments.validation;

import org.springframework.validation.annotation.Validated;

// 클래스 레벨 @Validated - MethodValidationPostProcessor의 AnnotationMatchingPointcut이
// 이 애노테이션이 붙은 클래스만 프록시 대상으로 고른다. 이게 없으면(UnvalidatedOrderServiceImpl
// 참고) 파라미터의 제약 애노테이션은 그냥 읽히지 않는 메타데이터로 남는다.
@Validated
public class OrderServiceImpl implements OrderService {

    private final InvocationCounter counter;

    public OrderServiceImpl(InvocationCounter counter) {
        this.counter = counter;
    }

    @Override
    public void placeOrder(String customerId, int quantity) {
        counter.increment("placeOrder");
    }

    @Override
    public void placeValidatedOrder(OrderRequest request) {
        counter.increment("placeValidatedOrder");
    }

    @Override
    public void placeExpressOrder(OrderRequest request) {
        counter.increment("placeExpressOrder");
    }

    @Override
    public String findCustomerName(String customerId) {
        counter.increment("findCustomerName");
        return "missing".equals(customerId) ? null : "customer-" + customerId;
    }

    @Override
    public void refreshViaSelfInvocation(String customerId, int quantity) {
        // this.placeOrder(...)는 프록시가 아니라 대상 객체를 직접 호출한다 - 파라미터
        // 검증이 통째로 우회된다(AOP self-invocation의 일반 원칙, 11·12주차와 동일).
        this.placeOrder(customerId, quantity);
    }
}
