package lab.experiments.importselector;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// 가장 단순한 형태 - @Import에 구체적인 @Configuration 클래스를 직접 준다. 대조군.
@Configuration
@Import(PlainGreetingConfig.class)
public class ScenarioAConfig {
}
