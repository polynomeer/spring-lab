package lab.experiments.importselector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnglishGreetingConfig {

    @Bean
    public Greeting englishGreeting() {
        return new Greeting("Hello");
    }
}
