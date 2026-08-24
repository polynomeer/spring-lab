package lab.experiments.composedannotation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = lab.experiments.composedannotation.scanned.LoggableService.class)
public class CarConfig {

    // @Fast의 value 기본값("fast-lane")이 @AliasFor를 통해 그대로 @Qualifier("fast-lane")로
    // 취급된다.
    @Fast
    @Bean
    public Engine defaultFastEngine() {
        return new Engine("fast-lane");
    }

    // 명시적으로 값을 준 경우도 똑같이 전달된다.
    @Fast("turbo")
    @Bean
    public Engine turboEngine() {
        return new Engine("turbo");
    }

    @Bean
    public EngineConsumer engineConsumer(@Fast("turbo") Engine engine) {
        return new EngineConsumer(engine);
    }
}
