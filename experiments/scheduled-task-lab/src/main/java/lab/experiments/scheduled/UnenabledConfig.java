package lab.experiments.scheduled;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 의도적으로 @EnableScheduling이 없다.
@Configuration
public class UnenabledConfig {

    @Bean
    public UnenabledTask unenabledTask() {
        return new UnenabledTask();
    }
}
