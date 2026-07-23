package lab.experiments.ioc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanFactoryLabTest {

    @Test
    @DisplayName("containsBean()은 BeanDefinition 등록 여부만으로 true/false를 반환한다")
    void containsBeanReflectsRegistrationNotInstantiation() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        assertThat(beanFactory.containsBean("paymentService")).isFalse();

        beanFactory.registerBeanDefinition(
                "paymentService", new RootBeanDefinition(PaymentService.class));

        assertThat(beanFactory.containsBean("paymentService")).isTrue();
    }

    @Test
    @DisplayName("singleton 빈은 getBean()을 여러 번 호출해도 동일 인스턴스를 반환한다")
    void singletonBeanIsSameInstanceAcrossGetBeanCalls() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition(
                "paymentService", new RootBeanDefinition(PaymentService.class));

        PaymentService first = beanFactory.getBean(PaymentService.class);
        PaymentService second = beanFactory.getBean(PaymentService.class);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("prototype 빈은 getBean()을 호출할 때마다 새 인스턴스를 반환한다")
    void prototypeBeanIsNewInstanceEveryCall() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(PaymentService.class);
        definition.setScope(AbstractBeanDefinition.SCOPE_PROTOTYPE);
        beanFactory.registerBeanDefinition("paymentService", definition);

        PaymentService first = beanFactory.getBean(PaymentService.class);
        PaymentService second = beanFactory.getBean(PaymentService.class);

        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("이름 기반 조회와 타입 기반 조회는 같은 빈 인스턴스를 반환한다")
    void lookupByNameAndByTypeReturnSameInstance() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition(
                "paymentService", new RootBeanDefinition(PaymentService.class));

        Object byName = beanFactory.getBean("paymentService");
        PaymentService byType = beanFactory.getBean(PaymentService.class);

        assertThat(byName).isSameAs(byType);
    }

    @Test
    @DisplayName("같은 타입의 빈이 두 개 등록되어 있으면 타입 기반 조회는 예외를 던진다")
    void typeLookupFailsWhenMultipleCandidatesExist() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition(
                "paymentServiceA", new RootBeanDefinition(PaymentService.class));
        beanFactory.registerBeanDefinition(
                "paymentServiceB", new RootBeanDefinition(PaymentService.class));

        assertThatThrownBy(() -> beanFactory.getBean(PaymentService.class))
                .isInstanceOf(NoUniqueBeanDefinitionException.class);
    }

    @Test
    @DisplayName("등록되지 않은 빈을 조회하면 NoSuchBeanDefinitionException이 발생한다")
    void lookupOfUnregisteredBeanThrows() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        assertThatThrownBy(() -> beanFactory.getBean(PaymentService.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
