package lab.minispring.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleBeanFactoryTest {

    @Test
    @DisplayName("등록한 인스턴스를 이름으로 조회할 수 있다")
    void registeredInstanceCanBeLookedUpByName() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        PaymentService paymentService = new PaymentService();

        beanFactory.registerSingleton("paymentService", paymentService);

        assertThat(beanFactory.getBean("paymentService")).isSameAs(paymentService);
    }

    @Test
    @DisplayName("등록 전후 containsBean() 결과가 달라진다")
    void containsBeanReflectsRegistration() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        assertThat(beanFactory.containsBean("paymentService")).isFalse();

        beanFactory.registerSingleton("paymentService", new PaymentService());

        assertThat(beanFactory.containsBean("paymentService")).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 이름을 조회하면 NoSuchBeanException이 발생한다")
    void lookupOfUnregisteredNameThrows() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();

        assertThatThrownBy(() -> beanFactory.getBean("paymentService"))
                .isInstanceOf(NoSuchBeanException.class);
    }

    private static class PaymentService {
    }
}
