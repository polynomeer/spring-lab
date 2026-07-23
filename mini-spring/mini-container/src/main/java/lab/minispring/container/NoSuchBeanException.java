package lab.minispring.container;

public class NoSuchBeanException extends RuntimeException {

    public NoSuchBeanException(String beanName) {
        super("No bean named '" + beanName + "' is registered");
    }

    public NoSuchBeanException(Class<?> type) {
        super("No bean of type '" + type.getName() + "' is registered");
    }
}
