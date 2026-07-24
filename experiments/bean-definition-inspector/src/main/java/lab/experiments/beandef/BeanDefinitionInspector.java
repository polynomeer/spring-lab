package lab.experiments.beandef;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;

public final class BeanDefinitionInspector {

    private BeanDefinitionInspector() {
    }

    public static List<BeanDefinitionSummary> inspect(ConfigurableApplicationContext context) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        List<BeanDefinitionSummary> summaries = new ArrayList<>();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);

            boolean hasInstanceSupplier = definition instanceof AbstractBeanDefinition abd
                    && abd.getInstanceSupplier() != null;

            summaries.add(new BeanDefinitionSummary(
                    beanName,
                    definition.getBeanClassName(),
                    definition.getScope(),
                    definition.isLazyInit(),
                    definition.getFactoryBeanName(),
                    definition.getFactoryMethodName(),
                    hasInstanceSupplier,
                    definition.getRole()
            ));
        }

        return summaries;
    }

    public static void print(ConfigurableApplicationContext context) {
        for (BeanDefinitionSummary summary : inspect(context)) {
            System.out.printf(
                    """
                    beanName=%s
                    beanClass=%s
                    scope=%s
                    lazy=%s
                    factoryBean=%s
                    factoryMethod=%s
                    instanceSupplier=%s
                    role=%d
                    %n
                    """,
                    summary.beanName(),
                    summary.beanClassName(),
                    summary.scope(),
                    summary.lazyInit(),
                    summary.factoryBeanName(),
                    summary.factoryMethodName(),
                    summary.hasInstanceSupplier(),
                    summary.role()
            );
        }
    }
}
