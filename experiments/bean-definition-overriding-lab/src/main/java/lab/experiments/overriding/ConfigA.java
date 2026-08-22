package lab.experiments.overriding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigA {

    @Bean
    public Greeting greeting() {
        return new Greeting("from-A");
    }
}
