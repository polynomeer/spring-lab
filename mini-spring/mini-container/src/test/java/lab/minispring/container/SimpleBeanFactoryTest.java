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
    @DisplayName("생성자 파라미터를 해석할 빈이 없으면 NoSuchBeanException이 발생한다")
    void unresolvableConstructorParameterThrowsNoSuchBeanException() {
        // 단일 생성자는 project 15(생성자 주입)부터 자동으로 선택된다 - String 파라미터를
        // 해석하려다 등록된 String 빈이 없어서 실패한다. (이전에는 무조건 기본 생성자만
        // 찾다가 실패했지만, 지금은 실제로 파라미터를 해석하려고 시도한다.)
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("noDefaultCtor", new BeanDefinition(NoDefaultConstructorComponent.class));

        assertThatThrownBy(() -> beanFactory.getBean("noDefaultCtor"))
                .isInstanceOf(NoSuchBeanException.class);
    }

    @Test
    @DisplayName("리플렉션으로 만들 수 없는 클래스(추상 클래스)는 BeanInstantiationException이 발생한다")
    void trulyUninstantiableClassThrowsBeanInstantiationException() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("abstractComponent", new BeanDefinition(AbstractComponent.class));

        assertThatThrownBy(() -> beanFactory.getBean("abstractComponent"))
                .isInstanceOf(BeanInstantiationException.class);
    }

    @Test
    @DisplayName("registerSingleton()으로 등록한 인스턴스도 타입으로 조회할 수 있다")
    void typeLookupReturnsRegisteredSingletonInstance() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        PaymentService paymentService = new PaymentService();
        beanFactory.registerSingleton("paymentService", paymentService);

        assertThat(beanFactory.getBean(PaymentService.class)).isSameAs(paymentService);
    }

    @Test
    @DisplayName("BeanDefinition으로 등록한 빈도 타입으로 조회하면 생성돼서 반환된다")
    void typeLookupCreatesAndReturnsBeanDefinitionInstance() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class));

        CountingComponent bean = beanFactory.getBean(CountingComponent.class);

        assertThat(bean).isNotNull();
        assertThat(CountingComponent.constructorCalls).hasValue(1);
    }

    @Test
    @DisplayName("일치하는 빈이 없으면 타입 조회는 NoSuchBeanException을 던진다")
    void typeLookupWithNoCandidatesThrows() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();

        assertThatThrownBy(() -> beanFactory.getBean(PaymentService.class))
                .isInstanceOf(NoSuchBeanException.class);
    }

    @Test
    @DisplayName("같은 타입의 빈이 여러 개면 타입 조회는 NoUniqueBeanException을 던진다")
    void typeLookupWithMultipleCandidatesThrows() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerSingleton("paymentServiceA", new PaymentService());
        beanFactory.registerSingleton("paymentServiceB", new PaymentService());

        assertThatThrownBy(() -> beanFactory.getBean(PaymentService.class))
                .isInstanceOf(NoUniqueBeanException.class);
    }

    @Test
    @DisplayName("타입 조회는 일치하지 않는 다른 BeanDefinition 후보를 생성하지 않는다")
    void typeLookupDoesNotInstantiateNonMatchingCandidates() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("counting", new BeanDefinition(CountingComponent.class));
        beanFactory.registerBeanDefinition("payment", new BeanDefinition(PaymentService.class));

        beanFactory.getBean(PaymentService.class);

        assertThat(CountingComponent.constructorCalls).hasValue(0);
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

    private abstract static class AbstractComponent {
    }
}
