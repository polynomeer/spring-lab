package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

// 아무 조건 없이 항상 AuditLog를 등록한다 - GreetingAutoConfiguration이 이 빈을 주입받으므로,
// 실제로는 의존성 때문에라도 이 자동 설정이 먼저 처리돼야 한다. @AutoConfiguration(after=...)로
// 이 순서를 "의존성과 무관하게 선언적으로도" 보장하는 것이 이번 실험의 핵심이다.
@AutoConfiguration
public class LoggingSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditLog auditLog() {
        RegistrationOrder.record(LoggingSupportAutoConfiguration.class.getSimpleName());
        return new AuditLog();
    }
}
