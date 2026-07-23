package lab.minispring.container;

public class DuplicateBeanDefinitionException extends RuntimeException {

    public DuplicateBeanDefinitionException(String beanName) {
        super("A bean named '" + beanName + "' is already registered");
    }
}
