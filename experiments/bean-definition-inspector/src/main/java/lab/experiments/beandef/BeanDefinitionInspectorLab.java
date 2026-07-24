package lab.experiments.beandef;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanDefinitionInspectorLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = buildContext();
        BeanDefinitionInspector.print(context);
        context.close();
    }

    static AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        // @Component (via @ComponentScan) and @Bean are both registered when AppConfig is processed.
        context.register(AppConfig.class);

        // registerBeanDefinition()으로 직접 등록.
        context.registerBeanDefinition("manualBean", new RootBeanDefinition(ManualBean.class));

        // 정적 팩토리 메서드 기반: factoryBean 없이 beanClass + factoryMethodName만 지정.
        RootBeanDefinition legacyClientDefinition = new RootBeanDefinition(LegacyClient.class);
        legacyClientDefinition.setFactoryMethodName("create");
        context.registerBeanDefinition("legacyClient", legacyClientDefinition);

        // Supplier 기반.
        context.registerBeanDefinition("supplierBean",
                BeanDefinitionBuilder.genericBeanDefinition(SupplierBean.class, SupplierBean::new)
                        .getBeanDefinition());

        context.refresh();
        return context;
    }
}
