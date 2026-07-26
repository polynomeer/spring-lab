package lab.minispring.container;

public class AmbiguousConstructorException extends RuntimeException {

    public AmbiguousConstructorException(String beanName, Class<?> beanClass, String reason) {
        super("Cannot determine which constructor to use for bean '" + beanName
                + "' of type " + beanClass.getName() + ": " + reason);
    }
}
