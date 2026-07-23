package lab.minispring.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleBeanFactory {

    private final Map<String, BeanDefinition> beanDefinitionMap =
            new ConcurrentHashMap<>();

    private final Map<String, Object> singletonObjects =
            new ConcurrentHashMap<>();

    private final Deque<String> beanCreationPath = new ArrayDeque<>();

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
            return createBean(name, definition);
        }

        Object created = createBean(name, definition);
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

    private Object createBean(String name, BeanDefinition definition) {
        if (beanCreationPath.contains(name)) {
            throw new CircularDependencyException(describePath(name));
        }

        beanCreationPath.addLast(name);
        try {
            return instantiate(name, definition);
        } finally {
            beanCreationPath.removeLast();
        }
    }

    private String describePath(String reenteredName) {
        List<String> path = new ArrayList<>(beanCreationPath);
        path.add(reenteredName);
        return String.join(" -> ", path);
    }

    private Object instantiate(String name, BeanDefinition definition) {
        try {
            Constructor<?> constructor = definition.beanClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException e) {
            // A constructor that itself calls back into this factory (no constructor
            // injection yet, see project 15) can throw one of our own container
            // exceptions from inside newInstance(). Let it propagate as-is instead of
            // burying it under a fresh BeanInstantiationException at every recursion level.
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BeanInstantiationException(name, definition.beanClass(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanInstantiationException(name, definition.beanClass(), e);
        }
    }
}
