package lab.ext.timing;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MethodTimingBeanPostProcessorTest {

    @BeforeEach
    void resetLog() {
        TimingLog.reset();
    }

    @Test
    void beanWithMeasureTimeMethodOnInterfaceIsReplacedByJdkProxy() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();

        OrderService orderService = context.getBean(OrderService.class);

        assertThat(Proxy.isProxyClass(orderService.getClass())).isTrue();
        assertThat(orderService).isNotInstanceOf(OrderServiceImpl.class);

        context.close();
    }

    @Test
    void annotatedMethodInvocationIsRecordedButOthersAreNot() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();
        OrderService orderService = context.getBean(OrderService.class);

        orderService.placeOrder();
        orderService.cachedLookup();
        orderService.cachedLookup();

        assertThat(TimingLog.measuredMethodNames()).containsExactly("placeOrder");

        context.close();
    }

    @Test
    void proxyStillBehavesLikeTheOriginalBean() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();
        OrderService orderService = context.getBean(OrderService.class);

        assertThat(orderService.cachedLookup()).isEqualTo("cached");

        context.close();
    }

    @Test
    void beanWithoutAnyMeasureTimeMethodIsNotWrapped() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();

        NotificationService notificationService = context.getBean(NotificationService.class);

        assertThat(Proxy.isProxyClass(notificationService.getClass())).isFalse();
        assertThat(notificationService).isInstanceOf(NotificationServiceImpl.class);

        context.close();
    }

    @Test
    void beanWithoutInterfacesCannotBeProxiedByJdkDynamicProxy() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();

        LegacyReport legacyReport = context.getBean(LegacyReport.class);
        legacyReport.generate();

        assertThat(Proxy.isProxyClass(legacyReport.getClass())).isFalse();
        // @MeasureTime이 붙어 있어도 인터페이스가 없어서 측정되지 않는다 - 이 한계는
        // Spring ProxyFactory/CGLIB로 옮겨가야 풀린다 (11~12주차).
        assertThat(TimingLog.measuredMethodNames()).doesNotContain("generate");

        context.close();
    }
}
