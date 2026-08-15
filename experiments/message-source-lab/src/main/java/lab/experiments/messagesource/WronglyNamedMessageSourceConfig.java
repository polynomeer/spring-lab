package lab.experiments.messagesource;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

// CorrectlyNamedMessageSourceConfig와 완전히 같은 MessageSource를 만들지만, 빈 이름이
// "messageSource"가 아니다 - 대조군.
@Configuration
public class WronglyNamedMessageSourceConfig {

    @Bean
    public MessageSource myMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
