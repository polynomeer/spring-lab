package lab.experiments.aware;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwareConfig {

    @Bean
    public RecordingAwareBean recordingAwareBean() {
        return new RecordingAwareBean();
    }

    @Bean
    public CustomAwareBeanPostProcessor customAwareBeanPostProcessor() {
        return new CustomAwareBeanPostProcessor();
    }
}
