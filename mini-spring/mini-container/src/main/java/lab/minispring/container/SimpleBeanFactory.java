package lab.minispring.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
            String primaryMatch = findPrimaryCandidate(matches);
            if (primaryMatch != null) {
                return type.cast(getBean(primaryMatch));
            }
            throw new NoUniqueBeanException(type, matches);
        }

        return type.cast(getBean(matches.get(0)));
    }

    private String findPrimaryCandidate(List<String> names) {
        String primaryName = null;
        for (String name : names) {
            BeanDefinition definition = beanDefinitionMap.get(name);
            if (definition != null && definition.primary()) {
                if (primaryName != null) {
                    return null;
                }
                primaryName = name;
            }
        }
        return primaryName;
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
        // 생성자 주입(project 15)은 instantiate() 단계에서 이미 끝난다 - 필드/세터 주입은
        // 아직 없어서 이 단계는 여전히 비어 있다. 생성(instantiate)과 초기화(initializeBean)
        // 사이에 이 단계가 분리되어 있다는 파이프라인 모양 자체가 4주차의 핵심이라 빈
        // 상태로라도 남겨 둔다.
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

        Constructor<?> constructor = selectConstructor(name, definition.beanClass());
        Object[] arguments = resolveArguments(constructor);
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            // A constructor that itself calls back into this factory can throw one of
            // our own container exceptions from inside newInstance(). Let it propagate
            // as-is instead of burying it under a fresh BeanInstantiationException at
            // every recursion level.
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BeanInstantiationException(name, definition.beanClass(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanInstantiationException(name, definition.beanClass(), e);
        }
    }

    private Constructor<?> selectConstructor(String name, Class<?> beanClass) {
        Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
        if (constructors.length == 1) {
            // 생성자가 하나뿐이면 @MiniAutowired 없이도 그 생성자가 선택된다.
            return constructors[0];
        }

        List<Constructor<?>> autowired = new ArrayList<>();
        Constructor<?> noArgConstructor = null;
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(MiniAutowired.class)) {
                autowired.add(constructor);
            } else if (constructor.getParameterCount() == 0) {
                noArgConstructor = constructor;
            }
        }

        if (autowired.size() > 1) {
            throw new AmbiguousConstructorException(name, beanClass,
                    "multiple constructors annotated with @MiniAutowired");
        }
        if (autowired.size() == 1) {
            return autowired.get(0);
        }
        if (noArgConstructor != null) {
            // 생성자가 여럿인데 @MiniAutowired가 하나도 없으면 기본 생성자로 떨어진다 -
            // 나머지 생성자의 파라미터는 무시된다. 실제 Spring의
            // determineCandidateConstructors()도 이 경우 후보를 정하지 못하고 일반
            // 인스턴스화 경로로 넘긴다(9주차 문서 참고).
            return noArgConstructor;
        }
        throw new AmbiguousConstructorException(name, beanClass,
                "multiple constructors with no @MiniAutowired and no no-arg fallback");
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

    private Object[] resolveArguments(Executable executable) {
        // 각 파라미터를 해석한다 - @MiniQualifier가 있으면 이름으로, 없으면 타입으로
        // getBean()을 호출한다. 순환 참조가 있으면 beanCreationPath 재진입 감지가 그대로
        // 걸린다(createBean을 감싸는 기존 가드를 재사용).
        Parameter[] parameters = executable.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = resolveArgument(parameters[i]);
        }
        return arguments;
    }

    private Object resolveArgument(Parameter parameter) {
        MiniQualifier qualifier = parameter.getAnnotation(MiniQualifier.class);
        if (qualifier != null) {
            return getBean(qualifier.value());
        }
        return getBean(parameter.getType());
    }
}
