package lab.minispring.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    public void registerSingleton(String name, Object bean) {
        requireUnregistered(name);
        singletonObjects.put(name, bean);
    }

    public void registerBeanDefinition(String name, BeanDefinition definition) {
        requireUnregistered(name);
        beanDefinitionMap.put(name, definition);
    }

    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
        beanPostProcessors.add(beanPostProcessor);
    }

    public void destroySingletons() {
        for (Object bean : singletonObjects.values()) {
            if (bean instanceof DisposableBean disposableBean) {
                disposableBean.destroy();
            }
        }
        singletonObjects.clear();
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
            Object bean = instantiate(name, definition);
            populateBean(name, bean, definition);
            return initializeBean(name, bean);
        } finally {
            beanCreationPath.removeLast();
        }
    }

    private void populateBean(String name, Object bean, BeanDefinition definition) {
        // 의존성 주입은 아직 없다 - project 15(Mini Constructor Injector)에서 채울 자리.
        // 생성(instantiate)과 초기화(initializeBean) 사이에 이 단계가 분리되어 있다는
        // 파이프라인 모양 자체가 이번 주제의 핵심이라 빈 상태로라도 남겨 둔다.
    }

    private Object initializeBean(String name, Object bean) {
        Object wrapped = bean;
        for (BeanPostProcessor processor : beanPostProcessors) {
            wrapped = processor.postProcessBeforeInitialization(wrapped, name);
        }

        if (wrapped instanceof InitializingBean initializingBean) {
            initializingBean.afterPropertiesSet();
        }

        for (BeanPostProcessor processor : beanPostProcessors) {
            wrapped = processor.postProcessAfterInitialization(wrapped, name);
        }

        return wrapped;
    }

    private String describePath(String reenteredName) {
        List<String> path = new ArrayList<>(beanCreationPath);
        path.add(reenteredName);
        return String.join(" -> ", path);
    }

    private Object instantiate(String name, BeanDefinition definition) {
        if (definition.hasFactoryMethod()) {
            return instantiateViaFactoryMethod(name, definition);
        }

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

    private Object instantiateViaFactoryMethod(String name, BeanDefinition definition) {
        // 정적 팩토리 메서드(factoryBeanName 없음)는 project 13 범위 밖이다 - MiniBean 메서드는
        // 항상 설정 클래스의 인스턴스 메서드로만 다룬다.
        Object factoryBean = getBean(definition.factoryBeanName());
        Method method = findFactoryMethod(name, factoryBean.getClass(), definition.factoryMethodName());

        Object[] arguments = resolveArguments(method);
        try {
            method.setAccessible(true);
            return method.invoke(factoryBean, arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BeanInstantiationException(name, definition.beanClass(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanInstantiationException(name, definition.beanClass(), e);
        }
    }

    private Method findFactoryMethod(String name, Class<?> factoryClass, String methodName) {
        for (Method method : factoryClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new BeanInstantiationException(name, factoryClass,
                new NoSuchMethodException(factoryClass.getName() + "#" + methodName));
    }

    private Object[] resolveArguments(Method method) {
        // 각 파라미터 타입을 getBean(Class)로 해석한다 - 순환 참조가 있으면 beanCreationPath
        // 재진입 감지가 그대로 걸린다(createBean을 감싸는 기존 가드를 재사용).
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = getBean(parameterTypes[i]);
        }
        return arguments;
    }
}
