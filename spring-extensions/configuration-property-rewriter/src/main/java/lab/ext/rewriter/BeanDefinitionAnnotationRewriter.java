package lab.ext.rewriter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Rewrites BeanDefinition metadata (scope, lazy, primary, role, a default property value)
 * based on marker annotations on the bean class - all before any bean is instantiated.
 */
public class BeanDefinitionAnnotationRewriter implements BeanFactoryPostProcessor {

    private final List<String> processedBeanNames = new ArrayList<>();

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            processedBeanNames.add(beanName);

            Class<?> beanClass = resolveBeanClass(beanFactory, beanName);
            if (beanClass == null) {
                continue;
            }

            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);

            if (beanClass.isAnnotationPresent(ForcePrototype.class)) {
                definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            }
            if (beanClass.isAnnotationPresent(ForceLazy.class)) {
                definition.setLazyInit(true);
            }
            if (beanClass.isAnnotationPresent(ForcePrimary.class)) {
                definition.setPrimary(true);
            }
            if (beanClass.isAnnotationPresent(SupportRole.class)) {
                definition.setRole(BeanDefinition.ROLE_SUPPORT);
            }

            DefaultChannel defaultChannel = beanClass.getAnnotation(DefaultChannel.class);
            if (defaultChannel != null && !definition.getPropertyValues().contains("channel")) {
                definition.getPropertyValues().add("channel", defaultChannel.value());
            }
        }
    }

    public List<String> getProcessedBeanNames() {
        return List.copyOf(processedBeanNames);
    }

    private Class<?> resolveBeanClass(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            return beanFactory.getType(beanName);
        } catch (Exception e) {
            return null;
        }
    }
}
