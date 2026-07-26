package lab.minispring.autoproxy;

import java.lang.reflect.Method;

import lab.minispring.aop.MiniProxyFactory;
import lab.minispring.container.BeanPostProcessor;

// AOP가 빈 생성 후처리(BeanPostProcessor)와 연결되는 지점 - postProcessAfterInitialization은
// 컨테이너가 어떤 특정 애노테이션을 안다고 가정하지 않는다. "이 클래스의 인터페이스 메서드 중
// Advisor의 Pointcut에 매칭되는 게 하나라도 있는가"만으로 프록시 여부를 판단하므로,
// MiniTransactional이 아닌 다른 애노테이션 기반 Advisor를 넣어도 이 클래스는 그대로 재사용된다.
public final class MiniAutoProxyCreator implements BeanPostProcessor {

    private final MiniAdvisor advisor;

    public MiniAutoProxyCreator(MiniAdvisor advisor) {
        this.advisor = advisor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!hasEligibleMethod(bean.getClass())) {
            return bean;
        }

        MiniProxyFactory proxyFactory = new MiniProxyFactory(bean);
        proxyFactory.addInterceptor(new PointcutFilteringInterceptor(advisor));
        return proxyFactory.getProxy();
    }

    private boolean hasEligibleMethod(Class<?> beanClass) {
        for (Class<?> iface : beanClass.getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (advisor.getPointcut().matches(method)) {
                    return true;
                }
            }
        }
        return false;
    }
}
