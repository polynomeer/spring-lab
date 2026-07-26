package lab.experiments.configproxy;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ConfigurationProxyLab {

    public static void main(String[] args) {
        report("Full", FullConfiguration.class);
        report("Lite", LiteConfiguration.class);
        report("Component", ComponentConfiguration.class);
    }

    private static void report(String label, Class<?> configClass) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configClass);

        PaymentService containerBean = context.getBean("paymentService", PaymentService.class);
        OrderService orderService = context.getBean(OrderService.class);

        System.out.println(label + ": containerBean == orderService.getPaymentService() -> "
                + (containerBean == orderService.getPaymentService()));

        context.close();
    }
}
