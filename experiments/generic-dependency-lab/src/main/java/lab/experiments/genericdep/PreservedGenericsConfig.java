package lab.experiments.genericdep;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Bean 메서드의 반환 타입 자체가 제네릭 타입 인자를 그대로 선언한다 - 컨테이너가 실제
// 인스턴스를 만들어 보지 않고도(늦은 타입 판정, 4주차의 지연 생성 원칙) 이 메서드
// 시그니처만으로 "이 빈은 Converter<String, Integer>다"를 미리 알 수 있다.
@Configuration
public class PreservedGenericsConfig {

    @Bean
    public Converter<String, Integer> stringToInt() {
        return new StringToIntConverter();
    }

    @Bean
    public Converter<Integer, String> intToString() {
        return new IntToStringConverter();
    }

    @Bean
    public StringToIntConsumer consumer(Converter<String, Integer> converter) {
        return new StringToIntConsumer(converter);
    }
}
