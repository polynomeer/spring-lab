package lab.experiments.importselector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KoreanGreetingConfig {

    @Bean
    public Greeting koreanGreeting() {
        return new Greeting("안녕하세요");
    }
}
