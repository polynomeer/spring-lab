package lab.sampleapp.orderplatform.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import lab.sampleapp.orderplatform.payment.PaymentGatewayClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * project 32(mini-observability-starter)의 RequestObservationAutoConfigurationTest와 같은
 * 4가지 관점으로 검증한다: 기본 설정에서 빈 생성 / enabled=false면 생성하지 않음 / 사용자
 * 정의 빈이 있으면 자동 설정이 물러남 / 프로퍼티 바인딩.
 *
 * <p><b>직접 겪은 함정</b>: "사용자 정의 빈" 테스트를 처음엔 project 32처럼 중첩
 * {@code @Configuration} 클래스({@code static class UserClientConfig})로 만들고
 * {@code withUserConfiguration(...)}으로 등록했다. project 32에서는 문제없었지만, 거긴
 * 그 테스트 클래스가 자기 모듈만의 독립된 패키지에 있다. 이 모듈은 OrderPlatformConfig가
 * "lab.sampleapp.orderplatform" 전체를 스캔하는데, 이 테스트 클래스도 바로 그 패키지
 * 트리 아래(lab.sampleapp.orderplatform.boot)에 있어서 그 중첩 설정 클래스까지 컴포넌트
 * 스캔에 걸려 버렸다 - OrderPlatformConfig로 만든 다른(무관한) 테스트에 엉뚱한
 * PaymentGatewayClient/NotificationDispatcher가 슬쩍 끼어드는 실제 오염을 겪었다.
 * withBean(...)으로 바꾸면 스캔 가능한 새 클래스를 아예 만들지 않고 빈을 직접 등록할 수
 * 있어 이 위험이 원천적으로 없다.
 */
class PaymentGatewayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentGatewayAutoConfiguration.class));

    @Test
    void defaultConfigurationRegistersThePaymentGatewayClient() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(PaymentGatewayClient.class));
    }

    @Test
    void disablingThePropertyPreventsRegistration() {
        contextRunner.withPropertyValues("order-platform.payment.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentGatewayClient.class));
    }

    @Test
    void userDefinedClientMakesTheAutoConfigurationBackOff() {
        PaymentGatewayClient userClient = new PaymentGatewayClient();
        contextRunner.withBean(PaymentGatewayClient.class, () -> userClient).run(context -> {
            assertThat(context).hasSingleBean(PaymentGatewayClient.class);
            assertThat(context.getBean(PaymentGatewayClient.class)).isSameAs(userClient);
        });
    }

    @Test
    void connectTimeoutPropertyBindsAndFlowsIntoTheCreatedClient() {
        // enabled 자체를 false로 하면(다른 테스트) 클래스 레벨 @ConditionalOnProperty 때문에
        // @EnableConfigurationProperties까지 통째로 건너뛰어져 PaymentGatewayProperties
        // 빈조차 없다 - 그래서 바인딩 검증은 게이트가 아닌 다른 프로퍼티로 해야 한다.
        contextRunner.withPropertyValues("order-platform.payment.connect-timeout-seconds=30")
                .run(context -> {
                    PaymentGatewayProperties properties = context.getBean(PaymentGatewayProperties.class);
                    assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(30);
                    assertThat(context.getBean(PaymentGatewayClient.class).connectTimeoutSeconds()).isEqualTo(30);
                });
    }
}
