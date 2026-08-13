package lab.experiments.scheduled;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class FlakyConfig {

    @Bean
    public FlakyTask flakyTask() {
        return new FlakyTask();
    }
}
