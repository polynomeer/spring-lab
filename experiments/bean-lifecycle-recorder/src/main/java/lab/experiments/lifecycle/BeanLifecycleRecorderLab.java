package lab.experiments.lifecycle;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanLifecycleRecorderLab {

    public static void main(String[] args) {
        LifecycleEventLog.reset();

        AnnotationConfigApplicationContext context = buildContext();

        System.out.println("--- events recorded up to and including refresh() ---");
        LifecycleEventLog.events().forEach(System.out::println);

        context.close();

        System.out.println("--- events recorded after close() ---");
        LifecycleEventLog.events().forEach(System.out::println);
    }

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(
                LifecycleConfig.class,
                LoggingInstantiationAwareBeanPostProcessor.class,
                LoggingBeanPostProcessor.class,
                LoggingSmartInitializingSingleton.class,
                LoggingApplicationListener.class
        );
        context.refresh();
        return context;
    }
}
