package lab.sampleapp.orderplatform.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import lab.sampleapp.orderplatform.domain.Member;
import lab.sampleapp.orderplatform.notification.NotificationChannel;
import lab.sampleapp.orderplatform.notification.NotificationDispatcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentGatewayAutoConfigurationTest의 클래스 주석에 적어 둔 것과 같은 이유로, 사용자 정의
 * 빈/채널 빈은 전부 withBean(...)으로 직접 등록한다 - 중첩 @Configuration 클래스를
 * 새로 만들면 이 테스트 클래스 자신이 lab.sampleapp.orderplatform.boot 패키지에 있어서
 * OrderPlatformConfig의 컴포넌트 스캔에 함께 걸려 버린다.
 */
class NotificationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotificationAutoConfiguration.class));

    @Test
    void defaultConfigurationRegistersTheDispatcherEvenWithNoChannelsPresent() {
        // List<NotificationChannel>은 빈 리스트로도 정상 주입된다 - 0개짜리 컬렉션 주입은
        // 실패가 아니다(멤버 클래스 자체는 존재하는 게 자연스럽다는 판단).
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NotificationDispatcher.class);
            assertThat(context.getBean(NotificationDispatcher.class).channelTypesInOrder()).isEmpty();
        });
    }

    @Test
    void disablingThePropertyPreventsRegistration() {
        contextRunner.withPropertyValues("order-platform.notification.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NotificationDispatcher.class));
    }

    @Test
    void userDefinedDispatcherMakesTheAutoConfigurationBackOff() {
        NotificationDispatcher userDispatcher = new NotificationDispatcher(java.util.List.of());
        contextRunner.withBean(NotificationDispatcher.class, () -> userDispatcher).run(context -> {
            assertThat(context).hasSingleBean(NotificationDispatcher.class);
            assertThat(context.getBean(NotificationDispatcher.class)).isSameAs(userDispatcher);
        });
    }

    @Test
    void channelsDeclaredByUserConfigurationAreInjectedRegardlessOfImportOrder() {
        // NotificationAutoConfiguration의 클래스 주석에 적어 둔 이유 그대로 - 이 자동 설정은
        // @ConditionalOnBean(NotificationChannel.class)를 쓰지 않고 List<NotificationChannel>을
        // 빈 팩토리 메서드 파라미터로 받는다. 그 판단이 실제로 유효한지, 채널 빈이 뒤늦게
        // 발견되는 구성에서도 문제없이 주입되는지 직접 확인한다.
        NotificationChannel fakeChannel = new NotificationChannel() {
            @Override
            public String channelType() {
                return "fake";
            }

            @Override
            public void send(Member to, String message) {
            }
        };

        contextRunner.withBean(NotificationChannel.class, () -> fakeChannel)
                .run(context -> {
                    NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
                    assertThat(dispatcher.channelTypesInOrder()).containsExactly("fake");
                });
    }
}
