package lab.experiments.overriding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ConfigA와 완전히 같은 빈 이름("greeting")을 쓴다 - 의도적인 충돌.
@Configuration
public class ConfigB {

    @Bean
    public Greeting greeting() {
        return new Greeting("from-B");
    }
}
