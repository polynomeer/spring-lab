package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnSlowMode
public class SlowModeAutoConfiguration {

    @Bean
    public SlowModeMarker slowModeMarker() {
        return new SlowModeMarker();
    }
}
