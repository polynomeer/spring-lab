package lab.minispring.container;

public class NoSuchBeanException extends RuntimeException {

    public NoSuchBeanException(String beanName) {
        super("No bean named '" + beanName + "' is registered");
    }
}
