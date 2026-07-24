package lab.experiments.refresh;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class LoggingBeanPostProcessor implements BeanPostProcessor {

    private static final Set<String> TRACKED_BEAN_NAMES =
            Set.of("eagerSingleton", "lazySingleton", "prototypeBean");

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (TRACKED_BEAN_NAMES.contains(beanName)) {
            RefreshEventLog.record("BPP:before:" + beanName);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (TRACKED_BEAN_NAMES.contains(beanName)) {
            RefreshEventLog.record("BPP:after:" + beanName);
        }
        return bean;
    }
}
