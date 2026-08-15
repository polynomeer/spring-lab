package lab.experiments.factorybean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AutowiringConfig {

    @Bean
    public SingletonProductFactoryBean widget() {
        return new SingletonProductFactoryBean();
    }

    @Bean
    public WidgetConsumer widgetConsumer() {
        return new WidgetConsumer();
    }
}
