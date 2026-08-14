package lab.experiments.conversion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.support.DefaultConversionService;

@Configuration
public class CorrectlyNamedConversionServiceConfig {

    // static이어야 한다 - BeanFactoryPostProcessor(그리고 PropertySourcesPlaceholderConfigurer가
    // 바로 그것이다)는 다른 @Configuration 클래스들이 완전히 처리되기 전, 훨씬 이른 시점에
    // 인스턴스화돼야 하기 때문이다. 이번 실험의 핵심은 아니지만 놓치기 쉬운 전제 조건이라
    // 남겨 둔다.
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    // 빈 이름이 정확히 "conversionService"다 - ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME
    // 상수와 글자 그대로 일치해야 AbstractApplicationContext#prepareBeanFactory()가 이 빈을
    // 찾아서 beanFactory.setConversionService()로 등록해 준다.
    @Bean
    public DefaultConversionService conversionService() {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new StringToPointConverter());
        return conversionService;
    }

    @Bean
    public ConvertiblePropertiesHolder holder() {
        return new ConvertiblePropertiesHolder();
    }
}
