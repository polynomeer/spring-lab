package lab.experiments.objectprovider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TwoWithPrimaryGreeterConfig {

    @Bean
    @Primary
    public Greeter englishGreeter() {
        return new EnglishGreeter();
    }

    @Bean
    public Greeter koreanGreeter() {
        return new KoreanGreeter();
    }

    @Bean
    public GreeterConsumer greeterConsumer(ObjectProvider<Greeter> greeters) {
        return new GreeterConsumer(greeters);
    }
}
