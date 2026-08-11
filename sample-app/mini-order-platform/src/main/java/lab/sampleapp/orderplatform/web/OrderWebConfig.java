package lab.sampleapp.orderplatform.web;

import java.util.List;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lab.sampleapp.orderplatform.boot.OrderPlatformApplication;

/**
 * Phase 1~3의 OrderPlatformConfig와는 별도의 진입점이다 - 이쪽은 스캔 범위를 좁히지 않고
 * "lab.sampleapp.orderplatform" 전체(web 패키지 포함)를 스캔해서, MVC 계층과 그 아래
 * 모든 서비스/리포지토리/AOP 스택을 한 컨텍스트에 올린다. 웹이 필요 없는 Phase 1~3
 * 테스트는 여전히 OrderPlatformConfig만 등록하면 되고, MVC를 검증하는 테스트만 이 설정을
 * 쓴다.
 *
 * <p>OrderPlatformApplication은 제외한다 - OrderPlatformConfig의 클래스 주석에 적어 둔
 * 이유와 동일하다(그 클래스의 @EnableAutoConfiguration이 컴포넌트 스캔만으로 활성화되어
 * Boot 표준 자동 설정 전체를 끌고 들어온다).
 */
@Configuration
@EnableWebMvc
@ComponentScan(
        basePackages = "lab.sampleapp.orderplatform",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = OrderPlatformApplication.class))
public class OrderWebConfig implements WebMvcConfigurer {

    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;
    private final PaymentMethodConverter paymentMethodConverter;
    private final CurrentActorClearingInterceptor currentActorClearingInterceptor;

    public OrderWebConfig(
            CurrentMemberArgumentResolver currentMemberArgumentResolver,
            PaymentMethodConverter paymentMethodConverter,
            CurrentActorClearingInterceptor currentActorClearingInterceptor) {
        this.currentMemberArgumentResolver = currentMemberArgumentResolver;
        this.paymentMethodConverter = paymentMethodConverter;
        this.currentActorClearingInterceptor = currentActorClearingInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(paymentMethodConverter);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentActorClearingInterceptor);
    }
}
