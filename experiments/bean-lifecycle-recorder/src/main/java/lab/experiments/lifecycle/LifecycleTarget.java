package lab.experiments.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class LifecycleTarget
        implements BeanNameAware, BeanFactoryAware, ApplicationContextAware, InitializingBean, DisposableBean {

    private Dependency dependency;

    public LifecycleTarget() {
        LifecycleEventLog.record("constructor");
    }

    @Autowired
    public void inject(Dependency dependency) {
        this.dependency = dependency;
        LifecycleEventLog.record("@Autowired");
    }

    @Override
    public void setBeanName(String name) {
        LifecycleEventLog.record("BeanNameAware");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        LifecycleEventLog.record("BeanFactoryAware");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        LifecycleEventLog.record("ApplicationContextAware");
    }

    @PostConstruct
    public void postConstruct() {
        LifecycleEventLog.record("@PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        LifecycleEventLog.record("afterPropertiesSet");
    }

    public void customInit() {
        LifecycleEventLog.record("customInitMethod");
    }

    @PreDestroy
    public void preDestroy() {
        LifecycleEventLog.record("@PreDestroy");
    }

    @Override
    public void destroy() {
        LifecycleEventLog.record("destroy");
    }

    public void customDestroy() {
        LifecycleEventLog.record("customDestroyMethod");
    }

    public Dependency getDependency() {
        return dependency;
    }
}
