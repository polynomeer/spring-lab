package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 사용자가 GreetingService를 직접 등록해 뒀다 - GreetingAutoConfiguration의
// @ConditionalOnMissingBean(GreetingService.class)가 이걸 감지하고 물러나야 한다.
@Configuration
@EnableAutoConfiguration
public class AppConfigWithUserGreetingService {

    @Bean
    public GreetingService greetingService() {
        return new CustomGreetingService();
    }
}
