package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

// GreetingService/AuditLog 사이에는 아무 의존관계가 없다 - 그런데도 두 자동 설정의 처리
// 순서가 항상 LoggingSupportAutoConfiguration 다음이 되는 것을, 오직 after=... 선언만으로
// 강제한다는 것을 보여주기 위한 실험이다(의존성 때문에 어쩔 수 없이 순서가 생기는 것과
// 구분하기 위해 일부러 서로 의존하지 않게 만들었다).
@AutoConfiguration(after = LoggingSupportAutoConfiguration.class)
// matchIfMissing = true - 실제 Spring Boot의 관례처럼, 프로퍼티를 아예 안 준 사용자에게는
// "기본적으로 켜져 있다"는 경험을 준다. false로 명시했을 때만 꺼진다.
@ConditionalOnProperty(prefix = "greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GreetingAutoConfiguration {

    // 사용자가 GreetingService 빈을 직접 등록해 두면 이 자동 설정은 물러난다.
    @Bean
    @ConditionalOnMissingBean(GreetingService.class)
    public GreetingService greetingService() {
        RegistrationOrder.record(GreetingAutoConfiguration.class.getSimpleName());
        return new DefaultGreetingService();
    }
}
