package lab.ext.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// project 9 4단계: MethodTimingBeanPostProcessorTest와 같은 시나리오를 수동 BeanPostProcessor
// 대신 DefaultAdvisorAutoProxyCreator로 재현해서, 우리가 직접 짠 BeanPostProcessor의 로직
// (AopUtils.canApply + ProxyFactory)이 프레임워크가 자동으로 해 주는 것과 같은 결과를
// 낸다는 것을 확인한다.
class AutoProxyCreatorTest {

    @BeforeEach
    void resetLog() {
        TimingLog.reset();
    }

    @Test
    void advisorBeanIsAutomaticallyAppliedWithoutAnyHandWrittenBeanPostProcessor() {
        AnnotationConfigApplicationContext context = AutoProxyTimingLab.buildContext();

        OrderService orderService = context.getBean(OrderService.class);
        orderService.placeOrder();
        orderService.cachedLookup();

        assertThat(AopUtils.isAopProxy(orderService)).isTrue();
        assertThat(TimingLog.measuredMethodNames()).containsExactly("placeOrder");

        context.close();
    }

    @Test
    void beanWithoutAnyMeasureTimeMethodIsNotProxied() {
        AnnotationConfigApplicationContext context = AutoProxyTimingLab.buildContext();

        NotificationService notificationService = context.getBean(NotificationService.class);

        assertThat(AopUtils.isAopProxy(notificationService)).isFalse();

        context.close();
    }

    @Test
    void interfaceLessBeanIsStillProxiedByCglib() {
        AnnotationConfigApplicationContext context = AutoProxyTimingLab.buildContext();

        LegacyReport legacyReport = context.getBean(LegacyReport.class);
        legacyReport.generate();

        assertThat(AopUtils.isCglibProxy(legacyReport)).isTrue();
        assertThat(TimingLog.measuredMethodNames()).containsExactly("generate");

        context.close();
    }

    @Test
    void selfInvocationStillBypassesTheAutomaticallyCreatedProxy() {
        AnnotationConfigApplicationContext context = AutoProxyTimingLab.buildContext();
        OrderService orderService = context.getBean(OrderService.class);

        orderService.checkout();

        // 프록시를 누가 만들었는지(수동 BeanPostProcessor든 자동 프록시 생성기든)와
        // 무관하게, self-invocation은 여전히 프록시를 우회한다 - 11주차에서 확인한 것이
        // "우리 코드의 한계"가 아니라 "프록시 기반 AOP 자체의 구조적 한계"라는 뜻이다.
        assertThat(TimingLog.measuredMethodNames()).doesNotContain("placeOrder");

        context.close();
    }
}
