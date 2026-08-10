package lab.sampleapp.plugindiscovery;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDiscoveryTest {

    private AnnotationConfigApplicationContext buildContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(PluginDiscoveryConfig.class);
        context.refresh();
        return context;
    }

    @Test
    void packageScanDiscoversAllThreePlugins() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            assertThat(context.getBeansOfType(NotificationPlugin.class)).hasSize(3);
        }
    }

    @Test
    void springsAutomaticMapInjectionKeysByBeanNameNotByOurTypeMethod() {
        // 처음엔 Map<String, NotificationPlugin>을 그냥 주입받으면 키가 type()("email")일
        // 거라 예상했다 - 틀렸다. Spring의 Map<String, T> 자동 주입은 항상 빈 이름을 키로
        // 쓴다("emailNotificationPlugin") - 그래서 NotificationPluginRegistry가 따로 필요하다.
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var byBeanName = context.getBeansOfType(NotificationPlugin.class);
            assertThat(byBeanName.keySet()).containsExactlyInAnyOrder(
                    "slackNotificationPlugin", "emailNotificationPlugin", "smsNotificationPlugin");

            var registry = context.getBean(NotificationPluginRegistry.class);
            assertThat(registry.discoveredTypes()).containsExactlyInAnyOrder("slack", "email", "sms");
        }
    }

    @Test
    void orderedPluginsListReflectsAtOrderRegardlessOfEnabledState() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var registry = context.getBean(NotificationPluginRegistry.class);
            assertThat(registry.orderedPlugins()).extracting(NotificationPlugin::type)
                    .containsExactly("slack", "email", "sms");
        }
    }

    @Test
    void firstEnabledSkipsTheHigherPriorityButDisabledSlackPlugin() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var registry = context.getBean(NotificationPluginRegistry.class);
            assertThat(registry.firstEnabled()).map(NotificationPlugin::type).hasValue("email");
        }
    }

    @Test
    void duplicateTypeThrowsAtRegistryConstructionTime() {
        NotificationPlugin first = fakePlugin("push");
        NotificationPlugin second = fakePlugin("push");

        assertThatThrownBy(() -> new NotificationPluginRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate")
                .hasMessageContaining("push");
    }

    @Test
    void dispatchSendsToTheMatchingTypeAtRuntime() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var registry = context.getBean(NotificationPluginRegistry.class);
            var log = context.getBean(SentMessageLog.class);
            NotificationMessage message = new NotificationMessage("ada@example.com", "hi", "body");

            registry.dispatch("email", message);

            assertThat(log.entries()).extracting(SentMessageLog.Entry::pluginType).containsExactly("email");
        }
    }

    @Test
    void dispatchingToADisabledPluginThrowsInsteadOfSilentlySkipping() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var registry = context.getBean(NotificationPluginRegistry.class);
            NotificationMessage message = new NotificationMessage("ada@example.com", "hi", "body");

            assertThatThrownBy(() -> registry.dispatch("slack", message))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("disabled");
        }
    }

    @Test
    void dispatchingToAnUnknownTypeThrows() {
        try (AnnotationConfigApplicationContext context = buildContext()) {
            var registry = context.getBean(NotificationPluginRegistry.class);
            NotificationMessage message = new NotificationMessage("ada@example.com", "hi", "body");

            assertThatThrownBy(() -> registry.dispatch("fax", message))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static NotificationPlugin fakePlugin(String type) {
        return new NotificationPlugin() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void send(NotificationMessage message) {
            }
        };
    }
}
