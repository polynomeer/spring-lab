package lab.minispring.javaconfig;

import lab.minispring.container.SimpleBeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniConfigurationProcessorTest {

    @Test
    void parameterBasedFactoryMethodSharesTheSameSingleton() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        new MiniConfigurationProcessor().register(beanFactory, DemoConfig.class);

        PaymentService paymentService = (PaymentService) beanFactory.getBean("paymentService");
        OrderService orderService = (OrderService) beanFactory.getBean("orderService");

        assertThat(orderService.getPaymentService()).isSameAs(paymentService);
    }

    @Test
    void directMethodCallInsideConfigClassDoesNotShareTheSingleton() {
        // CGLIB 강화가 없어서 이 @MiniBean 메서드 안의 paymentService() 호출은 평범한 자바
        // 메서드 호출이다 - project 12(experiments/configuration-proxy-lab)의 Lite/Component
        // Configuration과 정확히 같은 결과.
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        new MiniConfigurationProcessor().register(beanFactory, DemoConfig.class);

        PaymentService paymentService = (PaymentService) beanFactory.getBean("paymentService");
        OrderService viaDirectCall = (OrderService) beanFactory.getBean("orderServiceViaDirectCall");

        assertThat(viaDirectCall.getPaymentService()).isNotSameAs(paymentService);
    }

    @Test
    void explicitBeanNameOverridesMethodName() {
        // 별도 설정 클래스를 쓴다 - DemoConfig에 같이 넣으면 PaymentService 타입 빈이 두 개가
        // 되어 orderService(PaymentService) 파라미터 해석이 모호해진다.
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        new MiniConfigurationProcessor().register(beanFactory, NamedBeanConfig.class);

        assertThat(beanFactory.containsBean("customPayment")).isTrue();
        assertThat(beanFactory.containsBean("namedPaymentService")).isFalse();
    }

    @Test
    void configurationClassItselfIsRegisteredAsABean() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        String configBeanName = new MiniConfigurationProcessor().register(beanFactory, DemoConfig.class);

        assertThat(configBeanName).isEqualTo("demoConfig");
        assertThat(beanFactory.getBean("demoConfig")).isInstanceOf(DemoConfig.class);
    }

    @Test
    void nonAnnotatedClassIsRejected() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();

        assertThatThrownBy(() -> new MiniConfigurationProcessor().register(beanFactory, Object.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static class PaymentService {
    }

    static class OrderService {

        private final PaymentService paymentService;

        OrderService(PaymentService paymentService) {
            this.paymentService = paymentService;
        }

        PaymentService getPaymentService() {
            return paymentService;
        }
    }

    @MiniConfiguration
    static class DemoConfig {

        @MiniBean
        PaymentService paymentService() {
            return new PaymentService();
        }

        @MiniBean
        OrderService orderService(PaymentService paymentService) {
            return new OrderService(paymentService);
        }

        @MiniBean
        OrderService orderServiceViaDirectCall() {
            return new OrderService(paymentService());
        }
    }

    @MiniConfiguration
    static class NamedBeanConfig {

        @MiniBean("customPayment")
        PaymentService namedPaymentService() {
            return new PaymentService();
        }
    }
}
