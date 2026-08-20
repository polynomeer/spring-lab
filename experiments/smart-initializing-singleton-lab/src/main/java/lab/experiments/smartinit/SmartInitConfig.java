package lab.experiments.smartinit;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class SmartInitConfig {

    @Bean
    public RecordingLifecycleLog recordingLifecycleLog() {
        return new RecordingLifecycleLog();
    }

    // beanB보다 먼저 선언 - "선언 순서와 afterSingletonsInstantiated 시점의 완전성은
    // 무관하다"는 것을 보여주기 위해 일부러 이 순서로 둔다.
    @Bean
    public BeanA beanA(RecordingLifecycleLog log, ApplicationContext context) {
        return new BeanA(log, context);
    }

    @Bean
    public BeanB beanB(RecordingLifecycleLog log) {
        return new BeanB(log);
    }

    @Bean
    public ContextRefreshedListener contextRefreshedListener(RecordingLifecycleLog log) {
        return new ContextRefreshedListener(log);
    }

    @Bean
    @Lazy
    public LazySmartBean lazySmartBean(RecordingLifecycleLog log) {
        return new LazySmartBean(log);
    }
}
