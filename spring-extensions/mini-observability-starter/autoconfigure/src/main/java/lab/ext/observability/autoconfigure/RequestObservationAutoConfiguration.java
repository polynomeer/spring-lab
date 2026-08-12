package lab.ext.observability.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import lab.ext.observability.core.RequestObservationInterceptor;
import lab.ext.observability.core.RequestObservationProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(RequestObservationProperties.class)
public class RequestObservationAutoConfiguration {

    // 실제 Spring Boot의 ObservationAutoConfiguration(spring-boot-actuator-autoconfigure)과
    // 같은 지점 - 애플리케이션이 이미 (예: actuator를 통해) ObservationRegistry를 갖고 있으면
    // 이 빈은 물러나고 그 레지스트리를 그대로 공유한다.
    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    // enabled=false면 이 빈 자체가 등록되지 않는다 - 인터셉터를 실제로 등록하는 아래
    // WebMvcConfigurer는 ObjectProvider로 "있으면 등록, 없으면 아무것도 안 함"만 하므로,
    // 이 빈이 없어도 안전하게 아무 일도 일어나지 않는다.
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "request-observation", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public RequestObservationInterceptor requestObservationInterceptor(RequestObservationProperties properties,
            ObservationRegistry observationRegistry) {
        return new RequestObservationInterceptor(properties, observationRegistry);
    }

    @Bean
    public WebMvcConfigurer requestObservationWebMvcConfigurer(
            ObjectProvider<RequestObservationInterceptor> interceptorProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                interceptorProvider.ifAvailable(registry::addInterceptor);
            }
        };
    }
}
