package lab.sampleapp.orderplatform.boot;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lab.sampleapp.orderplatform.notification.NotificationChannel;
import lab.sampleapp.orderplatform.notification.NotificationDispatcher;

/**
 * PaymentGatewayAutoConfiguration과 같은 이유로 NotificationDispatcher도
 * {@code @Component}를 떼고 여기로 옮겼다.
 *
 * <p><b>직접 겪은 함정(설계 단계에서 피한 것)</b>: 처음엔 "채널이 하나도 없으면 Dispatcher도
 * 만들지 않는다"는 의미로 {@code @ConditionalOnBean(NotificationChannel.class)}를 쓰려고
 * 했다. 하지만 이 자동 설정을 (진짜 @EnableAutoConfiguration 경로가 아니라) 컴포넌트
 * 스캔이나 평범한 @Import로 가져오는 경우, @ConditionalOnBean은 "다른 설정 클래스가
 * 아직 다 처리되지 않은 시점"에 평가될 수 있어 신뢰할 수 없다 - Boot 공식 문서가 명시적으로
 * 경고하는 함정이다(진짜 자동 설정 순서 보장은 AutoConfigurationImportSelector의 지연
 * 처리를 거칠 때만 적용된다). 그래서 대신 List&lt;NotificationChannel&gt; 자체를 빈 팩토리
 * 메서드의 파라미터로 받는 방식을 썼다 - 이건 조건 평가 시점이 아니라 실제 빈 생성 시점에
 * 해석되므로 순서에 영향받지 않는다(0개여도 빈 List가 주입될 뿐 실패하지 않는다).
 */
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnProperty(prefix = "order-platform.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationDispatcher notificationDispatcher(List<NotificationChannel> channels) {
        return new NotificationDispatcher(channels);
    }
}
