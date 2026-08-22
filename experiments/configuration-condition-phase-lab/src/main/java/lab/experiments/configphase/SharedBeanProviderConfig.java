package lab.experiments.configphase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedBeanProviderConfig {

    @Bean
    public SharedValue shared() {
        return new SharedValue("from-provider");
    }
}
