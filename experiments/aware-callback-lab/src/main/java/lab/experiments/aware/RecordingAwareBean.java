package lab.experiments.aware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.StringValueResolver;

// 열 개의 Aware 인터페이스를 전부 구현해서, 실제로 어떤 순서로 호출되는지를 직접 기록한다.
// 셋(BeanNameAware/BeanClassLoaderAware/BeanFactoryAware)은
// AbstractAutowireCapableBeanFactory#invokeAwareMethods()가 직접 호출하고, 나머지 일곱은
// ApplicationContextAwareProcessor(BeanPostProcessor)가 postProcessBeforeInitialization()
// 에서 호출한다 - 이 클래스 하나로 그 경계를 실행 순서로 재현한다.
public class RecordingAwareBean implements
        BeanNameAware, BeanClassLoaderAware, BeanFactoryAware,
        EnvironmentAware, EmbeddedValueResolverAware, ResourceLoaderAware,
        ApplicationEventPublisherAware, MessageSourceAware, ApplicationStartupAware,
        ApplicationContextAware {

    private final List<String> invocationOrder = new ArrayList<>();
    private BeanFactory beanFactory;
    private ApplicationContext applicationContext;

    @Override
    public void setBeanName(String name) {
        invocationOrder.add("BeanNameAware");
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        invocationOrder.add("BeanClassLoaderAware");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        invocationOrder.add("BeanFactoryAware");
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        invocationOrder.add("EnvironmentAware");
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        invocationOrder.add("EmbeddedValueResolverAware");
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        invocationOrder.add("ResourceLoaderAware");
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        invocationOrder.add("ApplicationEventPublisherAware");
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        invocationOrder.add("MessageSourceAware");
    }

    @Override
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        invocationOrder.add("ApplicationStartupAware");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        invocationOrder.add("ApplicationContextAware");
        this.applicationContext = applicationContext;
    }

    public List<String> invocationOrder() {
        return Collections.unmodifiableList(invocationOrder);
    }

    public BeanFactory beanFactory() {
        return beanFactory;
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }
}
