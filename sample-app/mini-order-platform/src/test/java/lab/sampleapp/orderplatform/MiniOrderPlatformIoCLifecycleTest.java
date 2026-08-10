package lab.sampleapp.orderplatform;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.domain.Member;
import lab.sampleapp.orderplatform.domain.MembershipTier;
import lab.sampleapp.orderplatform.discount.PricingService;
import lab.sampleapp.orderplatform.notification.NotificationDispatcher;
import lab.sampleapp.orderplatform.notification.SentNotificationLog;
import lab.sampleapp.orderplatform.payment.CardPaymentGateway;
import lab.sampleapp.orderplatform.payment.PaymentGateway;
import lab.sampleapp.orderplatform.payment.PaymentGatewayClient;
import lab.sampleapp.orderplatform.payment.PaymentGatewayRegistry;
import lab.sampleapp.orderplatform.payment.PaymentMethod;
import lab.sampleapp.orderplatform.payment.PaymentRequest;
import lab.sampleapp.orderplatform.payment.PaymentResult;
import lab.sampleapp.orderplatform.plugin.PluginCatalog;
import lab.sampleapp.orderplatform.plugin.PluginDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mini Order Platform 캡스톤 Phase 1(IoC 전략/타입별 플러그인/@Qualifier/컬렉션 주입,
 * 빈 생명주기 - 외부 클라이언트 초기화/종료, 커스텀 BeanPostProcessor) 검증.
 * docs/24-mini-order-platform/mini-order-platform.md 참고.
 */
class MiniOrderPlatformIoCLifecycleTest {

    private AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        return context;
    }

    @Test
    void paymentGatewayRegistryResolvesByMethodViaListToMapReconstruction() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            PaymentGatewayRegistry registry = context.getBean(PaymentGatewayRegistry.class);

            PaymentResult cardResult = registry.charge(PaymentMethod.CARD, new PaymentRequest("m-1", 10_000));
            PaymentResult pointResult = registry.charge(PaymentMethod.POINT, new PaymentRequest("m-1", 500));

            assertThat(cardResult.success()).isTrue();
            assertThat(cardResult.transactionId()).startsWith("PG-TXN-");
            assertThat(pointResult.success()).isTrue();
        }
    }

    @Test
    void duplicatePaymentMethodThrowsAtRegistryConstructionTime() {
        PaymentGateway first = fakeGateway(PaymentMethod.CARD);
        PaymentGateway second = fakeGateway(PaymentMethod.CARD);

        assertThatThrownBy(() -> new PaymentGatewayRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARD");
    }

    @Test
    void chargingAnUnregisteredMethodThrows() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            PaymentGatewayRegistry registry = context.getBean(PaymentGatewayRegistry.class);

            assertThatThrownBy(() -> registry.charge(
                    PaymentMethod.BANK_TRANSFER, new PaymentRequest("m-1", 1_000)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void pricingServicePicksTheMembershipQualifiedPolicyExplicitlyNotByDefault() {
        // 두 DiscountPolicy 구현체(NoDiscountPolicy, MembershipDiscountPolicy) 모두 컨텍스트에
        // 존재하지만(@Primary 없음), 생성자의 @Qualifier("membership")로 명시했기 때문에
        // 다른 후보가 있어도 모호성 예외 없이 정확히 그 빈이 선택된다.
        try (AnnotationConfigApplicationContext context = buildContext()) {
            assertThat(context.getBeansOfType(lab.sampleapp.orderplatform.discount.DiscountPolicy.class))
                    .hasSize(2);

            PricingService pricingService = context.getBean(PricingService.class);
            assertThat(pricingService.finalPriceWon(10_000)).isEqualTo(9_000);
        }
    }

    @Test
    void notificationDispatcherBroadcastsToEveryInjectedChannelInOrder() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
            SentNotificationLog log = context.getBean(SentNotificationLog.class);
            Member member = new Member("m-1", "Ada", MembershipTier.MEMBERSHIP);

            assertThat(dispatcher.channelTypesInOrder()).containsExactly("email", "sms");

            dispatcher.broadcast(member, "order placed");

            assertThat(log.entries()).extracting(SentNotificationLog.Entry::channelType)
                    .containsExactly("email", "sms");
        }
    }

    @Test
    void paymentGatewayClientConnectsOnRefreshAndDisconnectsOnClose() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();

        // 컨텍스트를 닫은 뒤에는 getBean()이 더 이상 허용되지 않으므로(IllegalStateException),
        // close() 전에 같은 인스턴스 참조를 미리 붙잡아 둔다 - close()가 그 참조가 가리키는
        // 객체의 @PreDestroy를 실제로 호출하는지는 참조를 통해서만 확인할 수 있다.
        PaymentGatewayClient client = context.getBean(PaymentGatewayClient.class);
        assertThat(client.isConnected()).isTrue();

        context.close();

        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void chargingBeforeTheClientHasConnectedThrows() {
        // 컨테이너를 거치지 않고 직접 인스턴스화하면 @PostConstruct가 절대 호출되지 않는다 -
        // DI 컨테이너가 실제로 하는 일(초기화 콜백 호출)이 "그냥 생성자 호출"과 다르다는 걸
        // 보여주는 경계 조건.
        PaymentGatewayClient rawClient = new PaymentGatewayClient();

        assertThatThrownBy(() -> rawClient.authorize(1_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void customBeanPostProcessorAutoRegistersEveryPluginRegardlessOfHowItWasDeclared() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            PluginCatalog catalog = context.getBean(PluginCatalog.class);

            assertThat(catalog.descriptors()).extracting(
                    PluginDescriptor::kind, PluginDescriptor::beanName, PluginDescriptor::key)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(
                                    "payment-gateway", "cardPaymentGateway", "CARD"),
                            org.assertj.core.groups.Tuple.tuple(
                                    "payment-gateway", "pointPaymentGateway", "POINT"),
                            org.assertj.core.groups.Tuple.tuple(
                                    "notification-channel", "emailNotificationChannel", "email"),
                            org.assertj.core.groups.Tuple.tuple(
                                    "notification-channel", "smsNotificationChannel", "sms"));
        }
    }

    private static PaymentGateway fakeGateway(PaymentMethod method) {
        return new PaymentGateway() {
            @Override
            public PaymentMethod method() {
                return method;
            }

            @Override
            public PaymentResult charge(PaymentRequest request) {
                return new PaymentResult(true, "fake", "fake");
            }
        };
    }
}
