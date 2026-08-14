package lab.experiments.conversion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.support.DefaultConversionService;

// CorrectlyNamedConversionServiceConfig와 완전히 같은 ConversionService를 만들지만, 빈
// 이름이 "conversionService"가 아니다 - 타입은 정확히 일치하는데도, ApplicationContext는
// "conversionService"라는 이름 하나만 본다(9번 절 - beanFactory.containsBean(고정된 문자열))
// 는 것을 대조군으로 확인하기 위한 설정.
@Configuration
public class WronglyNamedConversionServiceConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public DefaultConversionService myConversionService() {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new StringToPointConverter());
        return conversionService;
    }

    @Bean
    public ConvertiblePropertiesHolder holder() {
        return new ConvertiblePropertiesHolder();
    }
}
