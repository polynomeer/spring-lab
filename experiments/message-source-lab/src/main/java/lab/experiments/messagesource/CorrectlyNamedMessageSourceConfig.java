package lab.experiments.messagesource;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class CorrectlyNamedMessageSourceConfig {

    // 빈 이름이 정확히 "messageSource"다 -
    // AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME 상수와 글자 그대로 일치해야
    // initMessageSource()가 이 빈을 찾아서 쓴다. 30번(ConversionService)의
    // "conversionService"와 완전히 같은 명명 규약 패턴이다.
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
