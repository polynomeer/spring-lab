package lab.experiments.genericdep;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// PreservedGenericsConfig와 완전히 같은 구현체를 등록하지만, @Bean 메서드의 "선언된" 반환
// 타입에서 제네릭 타입 인자를 일부러 지워 뒀다(원시 타입 Converter). 실제 반환하는 객체는
// 여전히 클래스 선언부(implements Converter<String, Integer>)에 제네릭 정보를 온전히
// 갖고 있다 - "메서드 시그니처가 광고하는 타입"과 "실제 객체가 아는 타입" 중 어느 쪽을
// 컨테이너가 신뢰하는지가 이번 실험의 핵심 질문이다.
@Configuration
public class ErasedGenericsConfig {

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
    public StringToIntConsumer consumer(Converter<String, Integer> converter) {
        return new StringToIntConsumer(converter);
    }
}
