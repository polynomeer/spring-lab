package lab.ext.observability.autoconfigure;

import lab.ext.observability.core.ObservationLog;
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

    @Bean
    @ConditionalOnMissingBean
    public ObservationLog observationLog() {
        return new ObservationLog();
    }

    // enabled=false면 이 빈 자체가 등록되지 않는다 - 인터셉터를 실제로 등록하는 아래
    // WebMvcConfigurer는 ObjectProvider로 "있으면 등록, 없으면 아무것도 안 함"만 하므로,
    // 이 빈이 없어도 안전하게 아무 일도 일어나지 않는다.
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "request-observation", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public RequestObservationInterceptor requestObservationInterceptor(RequestObservationProperties properties,
            ObservationLog observationLog) {
        return new RequestObservationInterceptor(properties, observationLog);
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
