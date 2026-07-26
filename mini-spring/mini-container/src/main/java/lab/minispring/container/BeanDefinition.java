package lab.minispring.container;

public record BeanDefinition(
        Class<?> beanClass,
        Scope scope,
        String factoryBeanName,
        String factoryMethodName,
        boolean primary
) {

    public BeanDefinition(Class<?> beanClass) {
        this(beanClass, Scope.SINGLETON);
    }

    public BeanDefinition(Class<?> beanClass, Scope scope) {
        this(beanClass, scope, null, null, false);
    }

    public static BeanDefinition factoryMethod(Class<?> beanClass, Scope scope,
            String factoryBeanName, String factoryMethodName) {
        return new BeanDefinition(beanClass, scope, factoryBeanName, factoryMethodName, false);
    }

    public BeanDefinition asPrimary() {
        return new BeanDefinition(beanClass, scope, factoryBeanName, factoryMethodName, true);
    }

    public boolean hasFactoryMethod() {
        return factoryMethodName != null;
    }
}
