package lab.experiments.aware;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

// BeanPostProcessor 빈 자신도 ApplicationContextAware를 구현할 수 있다 - 그런데 그걸
// 채워 주는 ApplicationContextAwareProcessor 역시 또 다른 BeanPostProcessor다. 이 클래스가
// 실제로 applicationContext를 받을 수 있는지는, ApplicationContextAwareProcessor가 다른
// BeanPostProcessor 빈들보다 먼저 등록돼 있는지에 달려 있다.
public class CustomAwareBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }
}
