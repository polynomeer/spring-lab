package lab.experiments.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LifecycleConfig {

    @Bean
    public Dependency dependency() {
        return new Dependency();
    }

    // @Component 스캔으로는 initMethod/destroyMethod를 지정할 수 없어서 @Bean으로 등록한다.
    // @Autowired 세터 주입은 등록 경로와 무관하게 AutowiredAnnotationBeanPostProcessor가 그대로 처리한다.
    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public LifecycleTarget lifecycleTarget() {
        return new LifecycleTarget();
    }
}
