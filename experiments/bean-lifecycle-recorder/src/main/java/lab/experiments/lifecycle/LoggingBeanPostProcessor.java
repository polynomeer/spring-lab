package lab.experiments.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class LoggingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if ("lifecycleTarget".equals(beanName)) {
            LifecycleEventLog.record("BPP:beforeInitialization");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("lifecycleTarget".equals(beanName)) {
            LifecycleEventLog.record("BPP:afterInitialization");
        }
        return bean;
    }
}
