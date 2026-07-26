package lab.minispring.javaconfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import lab.minispring.container.BeanDefinition;
import lab.minispring.container.Scope;
import lab.minispring.container.SimpleBeanFactory;

/**
 * 설정 클래스를 읽어 @MiniBean 메서드마다 factory-method 기반 BeanDefinition을 등록한다.
 *
 * CGLIB 설정 클래스 프록시는 만들지 않는다(project 13의 명시된 제한사항) - 그래서 한
 * @MiniBean 메서드가 다른 @MiniBean 메서드를 "직접" 호출하면(getBean()을 거치지 않고) 그냥
 * 평범한 자바 메서드 호출이 되어 매번 새 객체가 만들어진다. 반면 메서드 파라미터로 의존성을
 * 선언하면 SimpleBeanFactory.instantiateViaFactoryMethod()가 getBean(paramType)으로
 * 해석하므로 컨테이너가 관리하는 싱글턴을 그대로 공유한다. 이 차이는 project 12
 * (experiments/configuration-proxy-lab)에서 확인한 Full vs Lite Configuration 차이와
 * 정확히 대응한다 - 여기엔 CGLIB 강화가 없으니 항상 "Lite"처럼 동작한다.
 */
public final class MiniConfigurationProcessor {

    public String register(SimpleBeanFactory beanFactory, Class<?> configClass) {
        if (!configClass.isAnnotationPresent(MiniConfiguration.class)) {
            throw new IllegalArgumentException(
                    configClass.getName() + " is not annotated with @MiniConfiguration");
        }

        String configBeanName = decapitalize(configClass.getSimpleName());
        beanFactory.registerSingleton(configBeanName, instantiateConfig(configClass));

        for (Method method : configClass.getDeclaredMethods()) {
            MiniBean miniBean = method.getAnnotation(MiniBean.class);
            if (miniBean == null) {
                continue;
            }

            String beanName = miniBean.value().isBlank() ? decapitalize(method.getName()) : miniBean.value();
            beanFactory.registerBeanDefinition(beanName, BeanDefinition.factoryMethod(
                    method.getReturnType(), Scope.SINGLETON, configBeanName, method.getName()));
        }

        return configBeanName;
    }

    private Object instantiateConfig(Class<?> configClass) {
        try {
            Constructor<?> constructor = configClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate configuration class " + configClass.getName(), e);
        }
    }

    private String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
