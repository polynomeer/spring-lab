package lab.experiments.configproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// proxyBeanMethods = false = Lite mode. CGLIB 서브클래스가 없으므로 orderService() 안의
// paymentService() 호출은 평범한 자바 메서드 호출 - 컨테이너를 거치지 않는다.
@Configuration(proxyBeanMethods = false)
public class LiteConfiguration {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentService());
    }
}
