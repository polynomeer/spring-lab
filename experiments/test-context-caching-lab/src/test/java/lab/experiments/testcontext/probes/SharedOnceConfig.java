package lab.experiments.testcontext.probes;

import lab.experiments.testcontext.ContextCreationCounter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedOnceConfig {

    @Bean
    public ContextCreationCounter contextCreationCounter() {
        return new ContextCreationCounter();
    }
}
