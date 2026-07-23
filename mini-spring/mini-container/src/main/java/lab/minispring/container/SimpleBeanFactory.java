package lab.minispring.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public <T> T getBean(Class<T> type) {
        List<String> matches = new ArrayList<>();
        for (String name : allRegisteredNames()) {
            if (matchesType(name, type)) {
                matches.add(name);
            }
        }

        if (matches.isEmpty()) {
            throw new NoSuchBeanException(type);
        }
        if (matches.size() > 1) {
            throw new NoUniqueBeanException(type, matches);
        }

        return type.cast(getBean(matches.get(0)));
    }

    public boolean containsBean(String name) {
        return singletonObjects.containsKey(name) || beanDefinitionMap.containsKey(name);
    }

    private Set<String> allRegisteredNames() {
        Set<String> names = new LinkedHashSet<>(singletonObjects.keySet());
        names.addAll(beanDefinitionMap.keySet());
        return names;
    }

    private boolean matchesType(String name, Class<?> type) {
        // An already-realized instance is checked directly (isInstance) so that
        // registerSingleton()-registered objects - which have no BeanDefinition/beanClass
        // metadata at all - are still matchable. A not-yet-created BeanDefinition is
        // checked against its declared beanClass instead of being instantiated just to
        // find out its type (see docs/01-ioc-container/bean-factory-getbean.md section 9's
        // getBeanByTypeWithPrimary finding).
        Object existing = singletonObjects.get(name);
        if (existing != null) {
            return type.isInstance(existing);
        }
        BeanDefinition definition = beanDefinitionMap.get(name);
        return definition != null && type.isAssignableFrom(definition.beanClass());
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
