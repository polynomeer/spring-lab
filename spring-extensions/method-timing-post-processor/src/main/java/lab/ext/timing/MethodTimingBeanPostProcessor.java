package lab.ext.timing;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * project 9 (Method Timing BeanPostProcessor) 2~3단계: java.lang.reflect.Proxy 대신
 * {@link ProxyFactory}를 쓰고, "이 빈을 감쌀지"와 "어떤 메서드에 어드바이스를 걸지"를
 * {@link Advisor}(Pointcut + Advice) 하나로 위임한다. 4단계(자동 프록시 생성기와 비교)는
 * {@link AutoProxyTimingConfig}에서 {@code DefaultAdvisorAutoProxyCreator}로 같은 Advisor를
 * 자동으로 적용해 이 수동 구현과 비교한다.
 */
public class MethodTimingBeanPostProcessor implements BeanPostProcessor {

    private final Advisor advisor;

    public MethodTimingBeanPostProcessor(Advisor advisor) {
        this.advisor = advisor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!AopUtils.canApply(advisor, bean.getClass())) {
            return bean;
        }

        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.addAdvisor(advisor);
        return proxyFactory.getProxy();
    }
}
