package lab.minispring.container;

public record BeanDefinition(
        Class<?> beanClass,
        Scope scope
) {

    public BeanDefinition(Class<?> beanClass) {
        this(beanClass, Scope.SINGLETON);
    }
}
