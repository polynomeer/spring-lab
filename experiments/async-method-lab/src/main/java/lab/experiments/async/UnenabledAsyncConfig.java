package lab.experiments.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 의도적으로 @EnableAsync가 없다.
@Configuration
public class UnenabledAsyncConfig {

    @Bean
    public UnenabledAsyncService unenabledAsyncService() {
        return new UnenabledAsyncService();
    }
}
