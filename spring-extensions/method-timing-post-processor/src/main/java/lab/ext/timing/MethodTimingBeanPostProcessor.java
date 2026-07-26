package lab.ext.timing;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * project 9 (Method Timing BeanPostProcessor), 1단계: java.lang.reflect.Proxy.
 * 2단계(Spring ProxyFactory), 3단계(Pointcut+Advisor), 4단계(자동 프록시 생성기 비교)는
 * 11~12주차(Spring AOP)에서 다시 다룬다 - docs/plan/01-roadmap.md 참고.
 */
@Component
public class MethodTimingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?>[] interfaces = bean.getClass().getInterfaces();

        // JDK 동적 프록시는 인터페이스가 있어야만 만들 수 있고, 그 인터페이스의 Method
        // 객체를 통해서만 애노테이션을 인식한다 - 둘 다 아니면 원본 빈을 그대로 돌려준다.
        if (interfaces.length == 0 || !anyMethodAnnotated(interfaces)) {
            return bean;
        }

        return Proxy.newProxyInstance(
                bean.getClass().getClassLoader(),
                interfaces,
                new TimingInvocationHandler(bean));
    }

    private boolean anyMethodAnnotated(Class<?>[] interfaces) {
        for (Class<?> iface : interfaces) {
            for (Method method : iface.getMethods()) {
                if (method.isAnnotationPresent(MeasureTime.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
