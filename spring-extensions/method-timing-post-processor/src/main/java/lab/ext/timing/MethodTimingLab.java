package lab.ext.timing;

import java.lang.reflect.Proxy;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MethodTimingLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = buildContext();

        OrderService orderService = context.getBean(OrderService.class);
        System.out.println("orderService is JDK proxy? = " + Proxy.isProxyClass(orderService.getClass()));

        orderService.placeOrder();
        orderService.cachedLookup();
        System.out.println("measured methods so far = " + TimingLog.measuredMethodNames());

        LegacyReport legacyReport = context.getBean(LegacyReport.class);
        System.out.println("legacyReport is JDK proxy? = " + Proxy.isProxyClass(legacyReport.getClass()));

        context.close();
    }

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TimingConfig.class);
        context.refresh();
        return context;
    }
}
