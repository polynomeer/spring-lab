package lab.experiments.configproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

// @Configuration이 아니라 @Component에 @Bean 메서드를 얹은 경우 - Spring은 이런 클래스를
// 자동으로 Lite 취급한다(CGLIB 강화 대상 자체가 아니다).
@Component
public class ComponentConfiguration {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentService());
    }
}
