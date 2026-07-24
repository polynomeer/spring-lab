package lab.experiments.beandef;

public record BeanDefinitionSummary(
        String beanName,
        String beanClassName,
        String scope,
        boolean lazyInit,
        String factoryBeanName,
        String factoryMethodName,
        boolean hasInstanceSupplier,
        int role
) {
}
