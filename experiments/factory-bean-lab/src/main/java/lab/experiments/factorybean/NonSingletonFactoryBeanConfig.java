package lab.experiments.factorybean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NonSingletonFactoryBeanConfig {

    @Bean
    public NonSingletonProductFactoryBean widget() {
        return new NonSingletonProductFactoryBean();
    }
}
