package lab.experiments.refresh;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChildConfig {

    @Bean
    public ChildOnlyBean childOnlyBean() {
        return new ChildOnlyBean();
    }

    @Bean
    public SharedBean shared() {
        return new SharedBean("child");
    }
}
