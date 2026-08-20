package lab.experiments.exposeproxy;

import org.springframework.aop.framework.AopContext;

public class OrderServiceImpl implements OrderService {

    // 애노테이션을 인터페이스가 아니라 구현 메서드에 직접 붙인다 - 자바는 애노테이션을
    // 구현 메서드에 자동으로 상속해 주지 않으므로, @annotation() 포인트컷이 안정적으로
    // 매칭되게 하려면(그리고 자동 프록시 생성기가 이 빈을 프록시 대상으로 판단하려면)
    // 여기 직접 있어야 한다.
    @Override
    @LoggedOperation
    public void placeOrder() {
        // 실제 주문 처리 로직 - 이번 실험의 관심사는 아님.
    }

    @Override
    public void placeOrderViaAopContextSelfInvocation() {
        ((OrderService) AopContext.currentProxy()).placeOrder();
    }
}
