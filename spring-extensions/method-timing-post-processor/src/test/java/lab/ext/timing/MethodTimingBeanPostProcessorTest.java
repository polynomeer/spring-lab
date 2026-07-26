package lab.ext.timing;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
    void beanWithoutInterfacesIsProxiedByCglibViaProxyFactory() {
        // 1단계(java.lang.reflect.Proxy)의 한계였던 인터페이스 없는 빈도, ProxyFactory로
        // 바꾼 뒤로는 CGLIB로 자동 전환되어 정상적으로 측정된다(11주차 proxy-playground의
        // noInterfaceTargetForcesCglibProxy와 같은 메커니즘).
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();

        LegacyReport legacyReport = context.getBean(LegacyReport.class);
        legacyReport.generate();

        assertThat(Proxy.isProxyClass(legacyReport.getClass())).isFalse();
        assertThat(AopUtils.isCglibProxy(legacyReport)).isTrue();
        assertThat(TimingLog.measuredMethodNames()).containsExactly("generate");

        context.close();
    }

    @Test
    void selfInvocationBypassesTheProxyAndIsNotMeasured() {
        AnnotationConfigApplicationContext context = MethodTimingLab.buildContext();
        OrderService orderService = context.getBean(OrderService.class);

        orderService.checkout();

        // checkout() 내부의 this.placeOrder() 호출은 프록시를 거치지 않으므로 측정되지 않는다.
        assertThat(TimingLog.measuredMethodNames()).doesNotContain("placeOrder");

        orderService.placeOrder();

        // 외부에서 프록시를 거쳐 직접 부르면 정상적으로 측정된다.
        assertThat(TimingLog.measuredMethodNames()).containsExactly("placeOrder");

        context.close();
    }
}
