package lab.experiments.tx;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TransactionPlaygroundLab {

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TransactionPlaygroundConfig.class);
        context.refresh();
        return context;
    }
}
