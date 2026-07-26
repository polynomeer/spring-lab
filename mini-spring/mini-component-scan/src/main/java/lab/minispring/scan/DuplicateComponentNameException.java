package lab.minispring.scan;

public class DuplicateComponentNameException extends RuntimeException {

    public DuplicateComponentNameException(String beanName, Class<?> first, Class<?> second) {
        super("Both '" + first.getName() + "' and '" + second.getName()
                + "' resolve to the bean name '" + beanName + "'");
    }
}
