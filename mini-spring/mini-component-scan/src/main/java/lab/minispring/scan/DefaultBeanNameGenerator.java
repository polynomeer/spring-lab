package lab.minispring.scan;

public final class DefaultBeanNameGenerator implements BeanNameGenerator {

    @Override
    public String generateName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
