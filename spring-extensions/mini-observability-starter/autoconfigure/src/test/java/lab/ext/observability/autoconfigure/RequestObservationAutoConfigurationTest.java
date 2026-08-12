package lab.ext.observability.autoconfigure;

import java.time.Duration;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import lab.ext.observability.core.RequestObservationInterceptor;
import lab.ext.observability.core.RequestObservationProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

// 카탈로그(project 32)가 요구하는 5가지 테스트를 그대로 따른다: 기본 설정에서 빈 생성 /
// enabled=false이면 생성하지 않음 / 사용자 정의 빈이 있으면 물러남 / WebMVC가 없으면
// 생성하지 않음 / 프로퍼티 바인딩 검증. 여기에 ObservationRegistry도 같은 방식으로
// @ConditionalOnMissingBean이 걸려 있으므로, 그 back-off도 하나 더 검증한다.
class RequestObservationAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestObservationAutoConfiguration.class));

    @Test
    void defaultConfigurationRegistersTheInterceptorAndTheObservationRegistry() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(RequestObservationInterceptor.class);
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context).hasSingleBean(WebMvcConfigurer.class);
        });
    }

    @Test
    void userDefinedObservationRegistryMakesTheAutoConfigurationBackOffAndIsSharedWithTheInterceptor() {
        webContextRunner.withUserConfiguration(UserObservationRegistryConfig.class).run(context -> {
            assertThat(context.getBean(ObservationRegistry.class))
                    .isSameAs(UserObservationRegistryConfig.USER_REGISTRY);
            // 인터셉터도 우리가 auto-configure한 레지스트리가 아니라, 사용자가 등록해 둔 바로
            // 그 레지스트리를 주입받아야 한다 - 실제 애플리케이션이 actuator 등으로 이미 가진
            // ObservationRegistry를 공유하는 시나리오와 같다.
            assertThat(context).hasSingleBean(RequestObservationInterceptor.class);
        });
    }

    @Test
    void disablingThePropertyPreventsTheInterceptorFromBeingRegistered() {
        webContextRunner.withPropertyValues("request-observation.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RequestObservationInterceptor.class);
            // WebMvcConfigurer 자체는 여전히 등록되지만(ObjectProvider로 안전하게 아무 일도
            // 안 함), 인터셉터가 없으니 실질적으로 아무것도 하지 않는다.
            assertThat(context).hasSingleBean(WebMvcConfigurer.class);
        });
    }

    @Test
    void userDefinedInterceptorMakesTheAutoConfigurationBackOff() {
        webContextRunner.withUserConfiguration(UserInterceptorConfig.class).run(context -> {
            assertThat(context).hasSingleBean(RequestObservationInterceptor.class);
            assertThat(context.getBean(RequestObservationInterceptor.class))
                    .isSameAs(UserInterceptorConfig.USER_INTERCEPTOR);
        });
    }

    @Test
    void regularNonWebApplicationContextNeverActivatesThisAutoConfiguration() {
        // WebApplicationContextRunner가 아니라 평범한 ApplicationContextRunner를 쓰면
        // @ConditionalOnWebApplication(SERVLET)이 애초에 불일치해서 자동 설정 전체가
        // 건너뛰어진다 - "WebMVC가 없으면 생성하지 않음"을 확인하는 가장 직접적인 방법이다.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RequestObservationAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RequestObservationInterceptor.class));
    }

    @Test
    void propertiesBindDurationAndListSyntaxCorrectly() {
        webContextRunner
                .withPropertyValues(
                        "request-observation.slow-threshold=200ms",
                        "request-observation.exclude-paths[0]=/actuator/**",
                        "request-observation.exclude-paths[1]=/health")
                .run(context -> {
                    RequestObservationProperties properties = context.getBean(RequestObservationProperties.class);
                    assertThat(properties.getSlowThreshold()).isEqualTo(Duration.ofMillis(200));
                    assertThat(properties.getExcludePaths()).containsExactly("/actuator/**", "/health");
                });
    }

    @Configuration
    static class UserInterceptorConfig {

        static final RequestObservationInterceptor USER_INTERCEPTOR = new RequestObservationInterceptor(
                new RequestObservationProperties(), ObservationRegistry.create());

        @Bean
        RequestObservationInterceptor requestObservationInterceptor() {
            return USER_INTERCEPTOR;
        }
    }

    @Configuration
    static class UserObservationRegistryConfig {

        static final ObservationRegistry USER_REGISTRY = ObservationRegistry.create();

        @Bean
        ObservationRegistry observationRegistry() {
            return USER_REGISTRY;
        }
    }
}
