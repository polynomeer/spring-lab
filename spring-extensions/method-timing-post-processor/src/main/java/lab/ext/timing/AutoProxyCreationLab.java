package lab.ext.timing;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Runnable driver for a tools/jdi-tracer session against the automatic AOP proxy
 * creation path (AbstractAutoProxyCreator). See
 * tools/jdi-tracer/specs/auto-proxy-creation-lab.txt for the breakpoint spec and
 * docs/12-auto-proxy-creator/auto-proxy-creator.md for the session write-up.
 */
public class AutoProxyCreationLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = AutoProxyTimingLab.buildContext();

        OrderService orderService = context.getBean(OrderService.class);
        LegacyReport legacyReport = context.getBean(LegacyReport.class);
        NotificationService notificationService = context.getBean(NotificationService.class);

        System.out.println("orderService isAopProxy? " + AopUtils.isAopProxy(orderService));
        System.out.println("legacyReport isCglibProxy? " + AopUtils.isCglibProxy(legacyReport));
        System.out.println("notificationService isAopProxy? " + AopUtils.isAopProxy(notificationService));

        orderService.placeOrder();
        legacyReport.generate();

        context.close();
    }
}
