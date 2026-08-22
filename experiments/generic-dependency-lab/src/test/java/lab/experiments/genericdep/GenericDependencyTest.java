package lab.experiments.genericdep;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericDependencyTest {

    @Test
    void beanMethodReturnTypeThatPreservesGenericsResolvesTheCorrectCandidate() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(PreservedGenericsConfig.class)) {
            StringToIntConsumer consumer = context.getBean(StringToIntConsumer.class);

            // Converter<String, Integer>와 Converter<Integer, String> 둘 다 컨테이너
            // 안에 있는데도, 원시 타입이 아니라 제네릭 타입 인자까지 보고 정확히
            // stringToInt만 골라 낸다 - 잘못된 쪽(IntToStringConverter)이 주입됐다면
            // 브리지 메서드의 내부 캐스팅에서 ClassCastException이 났을 것이다.
            assertThat(consumer.convert("42")).isEqualTo(42);
        }
    }

    @Test
    void erasingTheBeanMethodsReturnTypeMakesBothConvertersLookAmbiguous() {
        // 단일 인자 생성자는 register()와 refresh()를 곧바로 실행해 버려서 예외를
        // try-with-resources 시작 지점에서 못 잡는다 - refresh()를 직접 호출할 수 있도록
        // 무인자 생성자로 만든 뒤 따로 register()한다.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ErasedGenericsConfig.class);

            // @Bean 메서드가 원시 타입 Converter를 반환하도록 선언하면, 실제 반환하는
            // 객체(StringToIntConverter)가 여전히 클래스 선언부에 제네릭 정보를 온전히
            // 갖고 있는데도 컨테이너는 그걸 보지 않는다 - "타입을 몰라서 못 찾는다"가
            // 아니라 "구분할 근거가 없어서 둘 다 후보로 본다"는 모호함으로 실패한다.
            assertThatThrownBy(context::refresh)
                    .isInstanceOf(UnsatisfiedDependencyException.class)
                    .rootCause()
                    .isInstanceOf(NoUniqueBeanDefinitionException.class)
                    .hasMessageContaining("stringToIntRaw")
                    .hasMessageContaining("intToStringRaw");
        }
    }

    @Test
    void aQualifierResolvesTheAmbiguityThatErasedGenericsCauseByThemselves() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ErasedGenericsWithQualifierConfig.class)) {
            StringToIntConsumer consumer = context.getBean(StringToIntConsumer.class);

            // 제네릭 타입 인자로는 구분할 수 없었던 것을, 빈 이름을 직접 지정하는 것으로
            // 대신한다 - 10주차(@Primary/@Qualifier)에서 이미 배운 해법이 여기서도
            // 그대로 통한다.
            assertThat(consumer.convert("42")).isEqualTo(42);
        }
    }
}
