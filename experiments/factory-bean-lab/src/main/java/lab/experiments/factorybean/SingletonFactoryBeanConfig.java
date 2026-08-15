package lab.experiments.factorybean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SingletonFactoryBeanConfig {

    @Bean
    public SingletonProductFactoryBean widget() {
        return new SingletonProductFactoryBean();
    }
}
