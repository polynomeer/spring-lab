package lab.experiments.customscope;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class TenantConfig {

    @Bean
    @Scope("tenant")
    public TenantWidget tenantWidget() {
        return new TenantWidget();
    }
}
