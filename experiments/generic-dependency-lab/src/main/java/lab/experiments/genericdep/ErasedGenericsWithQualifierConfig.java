package lab.experiments.genericdep;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ErasedGenericsConfig와 같은 원시 타입 등록이지만, 소비자 쪽이 @Qualifier로 빈 이름을
// 직접 지정한다 - 제네릭 타입 정보가 없어서 못 하는 구분을, 이름으로 대신 명시한다.
@Configuration
public class ErasedGenericsWithQualifierConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public Converter stringToIntRaw() {
        return new StringToIntConverter();
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public Converter intToStringRaw() {
        return new IntToStringConverter();
    }

    @Bean
    public StringToIntConsumer consumer(@Qualifier("stringToIntRaw") Converter<String, Integer> converter) {
        return new StringToIntConsumer(converter);
    }
}
