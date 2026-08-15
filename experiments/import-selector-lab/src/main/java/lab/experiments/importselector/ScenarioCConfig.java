package lab.experiments.importselector;

import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRepeatedGreeting(repeatCount = 3, message = "Hi")
public class ScenarioCConfig {
}
