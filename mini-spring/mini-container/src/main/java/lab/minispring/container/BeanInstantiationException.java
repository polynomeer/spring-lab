package lab.minispring.container;

public class BeanInstantiationException extends RuntimeException {

    public BeanInstantiationException(String beanName, Class<?> beanClass, Throwable cause) {
        super("Failed to instantiate bean '" + beanName + "' of type " + beanClass.getName(), cause);
    }
}
