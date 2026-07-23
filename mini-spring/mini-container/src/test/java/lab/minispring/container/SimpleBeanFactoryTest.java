package lab.minispring.container;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleBeanFactoryTest {

    @BeforeEach
    void resetCounters() {
        CountingComponent.constructorCalls.set(0);
    }

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

    @Test
    @DisplayName("BeanDefinition만 등록한 시점에는 인스턴스가 생성되지 않는다")
    void beanDefinitionIsNotInstantiatedUntilFirstGetBean() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class));

        assertThat(beanFactory.containsBean("counting")).isTrue();
        assertThat(CountingComponent.constructorCalls).hasValue(0);

        beanFactory.getBean("counting");

        assertThat(CountingComponent.constructorCalls).hasValue(1);
    }

    @Test
    @DisplayName("singleton BeanDefinition은 getBean()을 여러 번 호출해도 동일 인스턴스를 반환한다")
    void singletonBeanDefinitionIsSameInstanceAcrossGetBeanCalls() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class, Scope.SINGLETON));

        Object first = beanFactory.getBean("counting");
        Object second = beanFactory.getBean("counting");

        assertThat(first).isSameAs(second);
        assertThat(CountingComponent.constructorCalls).hasValue(1);
    }

    @Test
    @DisplayName("prototype BeanDefinition은 getBean()을 호출할 때마다 새 인스턴스를 반환한다")
    void prototypeBeanDefinitionIsNewInstanceEveryCall() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class, Scope.PROTOTYPE));

        Object first = beanFactory.getBean("counting");
        Object second = beanFactory.getBean("counting");

        assertThat(first).isNotSameAs(second);
        assertThat(CountingComponent.constructorCalls).hasValue(2);
    }

    @Test
    @DisplayName("같은 이름을 두 번 등록하면 DuplicateBeanDefinitionException이 발생한다")
    void duplicateBeanNameRegistrationThrows() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class));

        assertThatThrownBy(() ->
                beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class)))
                .isInstanceOf(DuplicateBeanDefinitionException.class);
        assertThatThrownBy(() -> beanFactory.registerSingleton("counting", new CountingComponent()))
                .isInstanceOf(DuplicateBeanDefinitionException.class);
    }

    @Test
    @DisplayName("기본 생성자가 없는 빈은 BeanInstantiationException이 발생한다")
    void beanWithoutNoArgConstructorThrowsBeanInstantiationException() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("noDefaultCtor", new BeanDefinition(NoDefaultConstructorComponent.class));

        assertThatThrownBy(() -> beanFactory.getBean("noDefaultCtor"))
                .isInstanceOf(BeanInstantiationException.class);
    }

    private static class PaymentService {
    }

    private static class CountingComponent {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        CountingComponent() {
            constructorCalls.incrementAndGet();
        }
    }

    private static class NoDefaultConstructorComponent {

        NoDefaultConstructorComponent(String requiredArgument) {
        }
    }
}
