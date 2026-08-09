package lab.ext.clientregistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalClientRegistryTest {

    @Test
    void registersOneBeanPerConfiguredClientWithCorrectValues() {
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext("external-clients.yml")) {
            ExternalApiClient payment = context.getBean("paymentClient", ExternalApiClient.class);
            assertThat(payment.getBaseUrl()).isEqualTo("https://payment.example.com");
            assertThat(payment.getTimeoutMillis()).isEqualTo(3000);

            ExternalApiClient order = context.getBean("orderClient", ExternalApiClient.class);
            assertThat(order.getBaseUrl()).isEqualTo("https://order.example.com");
            assertThat(order.getTimeoutMillis()).isEqualTo(5000);
        }
    }

    @Test
    void dynamicallyRegisteredClientsAreVisibleToRegularConstructorInjection() {
        // ExternalApiClientConsumer는 @Component로 스캔됐을 뿐, ExternalClientRegistrar를
        // 전혀 모른다 - List<ExternalApiClient>가 정상적으로 채워진다는 건 동적으로 등록된
        // 빈이 일반 컴포넌트 스캔 빈과 완전히 동등한 DI 후보로 취급된다는 뜻이다.
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext("external-clients.yml")) {
            var clients = context.getBean(ExternalApiClientConsumer.class).getClients();
            assertThat(clients).extracting(ExternalApiClient::getName)
                    .containsExactlyInAnyOrder("paymentClient", "orderClient");
        }
    }

    @Test
    void dynamicClientBeansAreRegisteredAsApplicationRoleByDefault() {
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext("external-clients.yml")) {
            assertThat(context.getBeanFactory().getBeanDefinition("paymentClient").getRole())
                    .isEqualTo(BeanDefinition.ROLE_APPLICATION);
        }
    }

    @Test
    void infrastructureRoleDoesNotExcludeTheBeanFromAutowiring() {
        // role은 도구/로그가 "이게 사용자 빈인지 내부 배관인지"를 구분하려고 붙이는 메타데이터일
        // 뿐, DI 후보 선택 로직에는 전혀 관여하지 않는다 - ROLE_INFRASTRUCTURE로 등록해도
        // List<ExternalApiClient> 주입에는 여전히 잡힌다.
        ExternalClientRegistrar registrar =
                new ExternalClientRegistrar("external-clients.yml", BeanDefinition.ROLE_INFRASTRUCTURE);
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext(registrar, true)) {
            assertThat(context.getBeanFactory().getBeanDefinition("paymentClient").getRole())
                    .isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
            assertThat(context.getBean(ExternalApiClientConsumer.class).getClients()).hasSize(2);
        }
    }

    @Test
    void duplicateNameInConfigSilentlyOverridesTheEarlierRegistration() {
        // registerBeanDefinition은 기본적으로(allowBeanDefinitionOverriding=true) 같은 이름을
        // 예외 없이 덮어쓴다 - 설정 파일에 이름이 중복되면 "나중에 나온 항목이 이긴다".
        ExternalClientRegistrar registrar = new ExternalClientRegistrar("external-clients-duplicate.yml");
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext(registrar, true)) {
            ExternalApiClient payment = context.getBean("paymentClient", ExternalApiClient.class);
            assertThat(payment.getBaseUrl()).isEqualTo("https://payment-v2.example.com");
            assertThat(payment.getTimeoutMillis()).isEqualTo(9000);
        }
    }

    @Test
    void duplicateNameThrowsWhenOverridingIsDisallowed() {
        // registerBeanDefinition()이 던지는 그대로 곧장 refresh() 밖으로 전파된다 - 다른
        // BeanCreationException류로 감싸지지 않는다(직접 확인 전엔 감싸질 거라 예상했다).
        ExternalClientRegistrar registrar = new ExternalClientRegistrar("external-clients-duplicate.yml");
        assertThatThrownBy(() -> DynamicClientRegistryLab.buildContext(registrar, false))
                .isInstanceOf(BeanDefinitionOverrideException.class);
    }

    @Test
    void missingConfigFileRegistersNothingWithoutFailingTheContext() {
        ExternalClientRegistrar registrar = new ExternalClientRegistrar("does-not-exist.yml");
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext(registrar, true)) {
            assertThat(context.getBean(ExternalApiClientConsumer.class).getClients()).isEmpty();
        }
    }

    @Test
    void emptyClientListRegistersNothing() {
        ExternalClientRegistrar registrar = new ExternalClientRegistrar("external-clients-empty.yml");
        try (AnnotationConfigApplicationContext context = DynamicClientRegistryLab.buildContext(registrar, true)) {
            assertThat(context.getBean(ExternalApiClientConsumer.class).getClients()).isEmpty();
        }
    }
}
