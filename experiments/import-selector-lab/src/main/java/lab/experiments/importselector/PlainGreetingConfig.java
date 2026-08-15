package lab.experiments.importselector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlainGreetingConfig {

    @Bean
    public Greeting greeting() {
        return new Greeting("plain");
    }
}
