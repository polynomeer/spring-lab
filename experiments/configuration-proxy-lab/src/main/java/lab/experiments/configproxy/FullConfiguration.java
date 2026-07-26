package lab.experiments.configproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// proxyBeanMethods 기본값(true) = Full mode. CGLIB 서브클래스가 orderService() 안의
// paymentService() "직접 호출"을 가로채서 컨테이너 싱글턴을 돌려준다.
@Configuration
public class FullConfiguration {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentService());
    }

    @Bean
    public static PaymentService paymentServiceStatic() {
        return new PaymentService();
    }

    @Bean
    public static AuditService auditServiceStatic() {
        // static @Bean은 Full mode에서도 절대 프록시되지 않는다 (2주차에서 소스로 확인:
        // enhancementIsNotPresentForStaticMethods) - 이 직접 호출은 항상 새 인스턴스를 만든다.
        return new AuditService(paymentServiceStatic());
    }
}
