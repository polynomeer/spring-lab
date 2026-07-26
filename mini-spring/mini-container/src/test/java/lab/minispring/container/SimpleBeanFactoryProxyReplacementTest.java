package lab.minispring.container;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project 9(spring-extensions/method-timing-post-processor)에서 확인한 "BeanPostProcessor는
 * 빈을 완전히 다른 객체로 교체할 수 있다"는 것을, 우리 mini-container의 BeanPostProcessor
 * 위에서도 똑같이 재현한다. 그때 배운 교훈(@MeasureTime은 구현 클래스가 아니라 인터페이스
 * 메서드에 붙여야 JDK 동적 프록시가 인식한다)을 이번엔 처음부터 반영했다.
 */
class SimpleBeanFactoryProxyReplacementTest {

    @Test
    void beanPostProcessorReplacesInterfaceBackedBeanWithJdkProxy() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        TimingBeanPostProcessor timingProcessor = new TimingBeanPostProcessor();
        beanFactory.addBeanPostProcessor(timingProcessor);
        beanFactory.registerBeanDefinition("orderService", new BeanDefinition(OrderServiceImpl.class));

        OrderService orderService = (OrderService) beanFactory.getBean("orderService");

        assertThat(Proxy.isProxyClass(orderService.getClass())).isTrue();
        assertThat(orderService).isNotInstanceOf(OrderServiceImpl.class);

        orderService.placeOrder();
        orderService.cachedLookup();
        orderService.cachedLookup();

        // @MeasureTime이 붙은 메서드만 기록된다 - 나머지는 정상 동작하지만 측정되지 않는다.
        assertThat(timingProcessor.getMeasuredMethodNames()).containsExactly("placeOrder");
    }

    @Test
    void beanWithoutInterfacesIsReturnedUnwrapped() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        TimingBeanPostProcessor timingProcessor = new TimingBeanPostProcessor();
        beanFactory.addBeanPostProcessor(timingProcessor);
        beanFactory.registerBeanDefinition("legacyReport", new BeanDefinition(LegacyReport.class));

        Object legacyReport = beanFactory.getBean("legacyReport");

        assertThat(Proxy.isProxyClass(legacyReport.getClass())).isFalse();
        assertThat(legacyReport).isInstanceOf(LegacyReport.class);
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface MeasureTime {
    }

    private interface OrderService {

        @MeasureTime
        void placeOrder();

        String cachedLookup();
    }

    private static class OrderServiceImpl implements OrderService {

        @Override
        public void placeOrder() {
        }

        @Override
        public String cachedLookup() {
            return "cached";
        }
    }

    private static class LegacyReport {

        @MeasureTime
        public void generate() {
        }
    }

    private static class TimingBeanPostProcessor implements BeanPostProcessor {

        private final List<String> measuredMethodNames = new ArrayList<>();

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 0 || !anyMethodAnnotated(interfaces)) {
                return bean;
            }

            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces,
                    (proxy, method, args) -> {
                        if (method.isAnnotationPresent(MeasureTime.class)) {
                            measuredMethodNames.add(method.getName());
                        }
                        try {
                            return method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        private boolean anyMethodAnnotated(Class<?>[] interfaces) {
            for (Class<?> iface : interfaces) {
                for (Method method : iface.getMethods()) {
                    if (method.isAnnotationPresent(MeasureTime.class)) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<String> getMeasuredMethodNames() {
            return List.copyOf(measuredMethodNames);
        }
    }
}
