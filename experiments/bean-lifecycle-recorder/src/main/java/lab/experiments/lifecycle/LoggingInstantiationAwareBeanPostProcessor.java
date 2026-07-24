package lab.experiments.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class LoggingInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if ("lifecycleTarget".equals(beanName)) {
            LifecycleEventLog.record("InstantiationAwareBPP:beforeInstantiation");
        }
        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        if ("lifecycleTarget".equals(beanName)) {
            LifecycleEventLog.record("InstantiationAwareBPP:afterInstantiation");
        }
        return true;
    }
}
