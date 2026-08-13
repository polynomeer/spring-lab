package lab.experiments.scheduled;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SharedThreadConfig {

    @Bean
    public SharedThreadTask taskA() {
        return new SharedThreadTask();
    }

    @Bean
    public SharedThreadTask taskB() {
        return new SharedThreadTask();
    }
}
