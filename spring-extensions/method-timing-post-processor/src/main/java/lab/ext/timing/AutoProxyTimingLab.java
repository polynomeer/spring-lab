package lab.ext.timing;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AutoProxyTimingLab {

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AutoProxyTimingConfig.class);
        context.refresh();
        return context;
    }
}
