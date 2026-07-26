package lab.experiments.configproxy;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationProxyTest {

    @Test
    void fullModeInterceptsCrossBeanMethodCallsAndPreservesSingleton() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FullConfiguration.class);

        PaymentService containerBean = context.getBean("paymentService", PaymentService.class);
        OrderService orderService = context.getBean(OrderService.class);

        assertThat(orderService.getPaymentService()).isSameAs(containerBean);

        context.close();
    }

    @Test
    void liteModeDoesNotInterceptCrossBeanMethodCalls() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LiteConfiguration.class);

        PaymentService containerBean = context.getBean("paymentService", PaymentService.class);
        OrderService orderService = context.getBean(OrderService.class);

        assertThat(orderService.getPaymentService()).isNotSameAs(containerBean);

        context.close();
    }

    @Test
    void componentWithBeanMethodsBehavesLikeLiteMode() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ComponentConfiguration.class);

        PaymentService containerBean = context.getBean("paymentService", PaymentService.class);
        OrderService orderService = context.getBean(OrderService.class);

        assertThat(orderService.getPaymentService()).isNotSameAs(containerBean);

        context.close();
    }

    @Test
    void staticBeanMethodsAreNeverInterceptedEvenInFullMode() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FullConfiguration.class);

        PaymentService staticContainerBean = context.getBean("paymentServiceStatic", PaymentService.class);
        AuditService auditService = context.getBean(AuditService.class);

        // static @Bean 메서드끼리의 직접 호출은 Full mode에서도 프록시를 거치지 않는다.
        assertThat(auditService.getPaymentService()).isNotSameAs(staticContainerBean);

        context.close();
    }
}
