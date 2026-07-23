package lab.minispring.container;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleBeanFactory {

    private final Map<String, BeanDefinition> beanDefinitionMap =
            new ConcurrentHashMap<>();

    private final Map<String, Object> singletonObjects =
            new ConcurrentHashMap<>();

    public void registerSingleton(String name, Object bean) {
        requireUnregistered(name);
        singletonObjects.put(name, bean);
    }

    public void registerBeanDefinition(String name, BeanDefinition definition) {
        requireUnregistered(name);
        beanDefinitionMap.put(name, definition);
    }

    public Object getBean(String name) {
        Object singleton = singletonObjects.get(name);
        if (singleton != null) {
            return singleton;
        }

        BeanDefinition definition = beanDefinitionMap.get(name);
        if (definition == null) {
            throw new NoSuchBeanException(name);
        }

        if (definition.scope() == Scope.PROTOTYPE) {
            return instantiate(name, definition);
        }

        Object created = instantiate(name, definition);
        singletonObjects.put(name, created);
        return created;
    }

    public boolean containsBean(String name) {
        return singletonObjects.containsKey(name) || beanDefinitionMap.containsKey(name);
    }

    private void requireUnregistered(String name) {
        if (containsBean(name)) {
            throw new DuplicateBeanDefinitionException(name);
        }
    }

    private Object instantiate(String name, BeanDefinition definition) {
        try {
            Constructor<?> constructor = definition.beanClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BeanInstantiationException(name, definition.beanClass(), e);
        }
    }
}
