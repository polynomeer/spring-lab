package lab.ext.rewriter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * Removes bean registrations entirely - a registry-level structural change that a plain
 * BeanFactoryPostProcessor cannot safely do (ConfigurableListableBeanFactory has no
 * removeBeanDefinition). BeanDefinitionRegistryPostProcessor exists precisely for this.
 */
public class ExclusionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String beanName : registry.getBeanDefinitionNames()) {
            Class<?> beanClass = resolveBeanClass(registry, beanName);
            if (beanClass != null && beanClass.isAnnotationPresent(Excluded.class)) {
                registry.removeBeanDefinition(beanName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 등록소 구조 변경(빈 제거)은 postProcessBeanDefinitionRegistry에서 전부 끝낸다 -
        // 이 메서드가 호출되는 시점엔 이미 대상 빈이 존재하지 않는다.
    }

    private Class<?> resolveBeanClass(BeanDefinitionRegistry registry, String beanName) {
        try {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            return className == null ? null : Class.forName(className);
        } catch (Exception e) {
            return null;
        }
    }
}
