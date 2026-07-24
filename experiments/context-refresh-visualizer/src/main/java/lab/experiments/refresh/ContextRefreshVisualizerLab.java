package lab.experiments.refresh;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ContextRefreshVisualizerLab {

    public static void main(String[] args) {
        RefreshEventLog.reset();

        AnnotationConfigApplicationContext context = buildContext();

        System.out.println("--- events recorded during refresh() ---");
        RefreshEventLog.events().forEach(System.out::println);

        context.close();
    }

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(
                EagerSingleton.class,
                LazySingleton.class,
                PrototypeBean.class,
                LoggingBeanFactoryPostProcessor.class,
                LoggingBeanPostProcessor.class,
                LoggingApplicationListener.class
        );
        context.refresh();
        return context;
    }
}
