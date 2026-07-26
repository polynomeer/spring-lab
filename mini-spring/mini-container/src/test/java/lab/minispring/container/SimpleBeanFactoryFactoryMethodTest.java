package lab.minispring.container;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleBeanFactoryFactoryMethodTest {

    @Test
    void factoryMethodCreatesInstanceAndResolvesParametersFromContainer() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerSingleton("config", new TestConfig());
        beanFactory.registerBeanDefinition("paymentService",
                BeanDefinition.factoryMethod(TestPaymentService.class, Scope.SINGLETON, "config", "paymentService"));
        beanFactory.registerBeanDefinition("orderService",
                BeanDefinition.factoryMethod(TestOrderService.class, Scope.SINGLETON, "config", "orderService"));

        TestOrderService orderService = (TestOrderService) beanFactory.getBean("orderService");
        TestPaymentService paymentService = (TestPaymentService) beanFactory.getBean("paymentService");

        assertThat(orderService.paymentService).isSameAs(paymentService);
    }

    @Test
    void singletonFactoryMethodResultIsCachedAcrossGetBeanCalls() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerSingleton("config", new TestConfig());
        beanFactory.registerBeanDefinition("paymentService",
                BeanDefinition.factoryMethod(TestPaymentService.class, Scope.SINGLETON, "config", "paymentService"));

        assertThat(beanFactory.getBean("paymentService")).isSameAs(beanFactory.getBean("paymentService"));
    }

    @Test
    void prototypeFactoryMethodCreatesNewInstanceEveryCall() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerSingleton("config", new TestConfig());
        beanFactory.registerBeanDefinition("paymentService",
                BeanDefinition.factoryMethod(TestPaymentService.class, Scope.PROTOTYPE, "config", "paymentService"));

        assertThat(beanFactory.getBean("paymentService")).isNotSameAs(beanFactory.getBean("paymentService"));
    }

    @Test
    void missingFactoryMethodThrowsBeanInstantiationException() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerSingleton("config", new TestConfig());
        beanFactory.registerBeanDefinition("missing",
                BeanDefinition.factoryMethod(TestPaymentService.class, Scope.SINGLETON, "config", "doesNotExist"));

        assertThatThrownBy(() -> beanFactory.getBean("missing")).isInstanceOf(BeanInstantiationException.class);
    }

    private static class TestPaymentService {
    }

    private static class TestOrderService {

        final TestPaymentService paymentService;

        TestOrderService(TestPaymentService paymentService) {
            this.paymentService = paymentService;
        }
    }

    private static class TestConfig {

        TestPaymentService paymentService() {
            return new TestPaymentService();
        }

        TestOrderService orderService(TestPaymentService paymentService) {
            return new TestOrderService(paymentService);
        }
    }
}
