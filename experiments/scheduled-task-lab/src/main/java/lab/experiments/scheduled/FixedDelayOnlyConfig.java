package lab.experiments.scheduled;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class FixedDelayOnlyConfig {

    @Bean
    public FixedDelayTask fixedDelayTask() {
        return new FixedDelayTask();
    }
}
