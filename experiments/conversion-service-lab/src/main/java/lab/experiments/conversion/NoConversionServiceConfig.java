package lab.experiments.conversion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

// ConversionService 빈을 아예 등록하지 않은 대조군.
@Configuration
public class NoConversionServiceConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public ConvertiblePropertiesHolder holder() {
        return new ConvertiblePropertiesHolder();
    }
}
