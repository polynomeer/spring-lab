package lab.experiments.objectprovider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OneGreeterConfig {

    @Bean
    public Greeter englishGreeter() {
        return new EnglishGreeter();
    }

    @Bean
    public GreeterConsumer greeterConsumer(ObjectProvider<Greeter> greeters) {
        return new GreeterConsumer(greeters);
    }
}
