package lab.sampleapp.orderplatform.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lab.sampleapp.orderplatform.payment.PaymentGatewayClient;

/**
 * Phase 1에서 {@code @Component}로 직접 등록했던 PaymentGatewayClient를 Boot 스타일
 * 자동 설정으로 바꿔 봤다 - PaymentGatewayClient 자신은 이제 {@code @Component}가 아니라서
 * (제거했다), 이 클래스가 없으면 아무도 그 빈을 등록해 주지 않는다. OrderPlatformConfig,
 * OrderWebConfig 둘 다 이 boot 패키지까지 통째로 컴포넌트 스캔하므로(다른 @Configuration
 * 클래스와 동일하게 취급됨) 별도 @Import 없이도 그대로 동작한다 - 진짜 Spring Boot
 * 애플리케이션(OrderPlatformApplication)에서는 여기에 더해
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports를 통해
 * @EnableAutoConfiguration의 "자동 발견" 경로로도 찾아진다.
 */
@AutoConfiguration
@EnableConfigurationProperties(PaymentGatewayProperties.class)
@ConditionalOnProperty(prefix = "order-platform.payment", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PaymentGatewayClient paymentGatewayClient(PaymentGatewayProperties properties) {
        return new PaymentGatewayClient(properties.getConnectTimeoutSeconds());
    }
}
