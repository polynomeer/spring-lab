package lab.experiments.objectprovider;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectProviderTest {

    @Test
    void zeroCandidatesGetObjectThrowsNoSuchBeanDefinitionException() {
        withGreeters(ZeroGreeterConfig.class, greeters ->
                assertThatThrownBy(greeters::getObject).isInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void zeroCandidatesGetIfAvailableReturnsNull() {
        withGreeters(ZeroGreeterConfig.class, greeters ->
                assertThat(greeters.getIfAvailable()).isNull());
    }

    @Test
    void zeroCandidatesGetIfUniqueReturnsNull() {
        withGreeters(ZeroGreeterConfig.class, greeters ->
                assertThat(greeters.getIfUnique()).isNull());
    }

    @Test
    void oneCandidateAllThreeAccessorsReturnIt() {
        withGreeters(OneGreeterConfig.class, greeters -> {
            assertThat(greeters.getObject().greet()).isEqualTo("Hello");
            assertThat(greeters.getIfAvailable().greet()).isEqualTo("Hello");
            assertThat(greeters.getIfUnique().greet()).isEqualTo("Hello");
        });
    }

    @Test
    void ambiguousCandidatesGetObjectThrowsNoUniqueBeanDefinitionException() {
        withGreeters(TwoAmbiguousGreeterConfig.class, greeters ->
                assertThatThrownBy(greeters::getObject).isInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void ambiguousCandidatesGetIfAvailableAlsoThrowsBecauseItOnlyGuardsAgainstAbsenceNotAmbiguity() {
        // ObjectProvider Javadoc: "both methods[getObject/getIfAvailable] will throw a
        // NoUniqueBeanDefinitionException if more than one matching bean is found" -
        // getIfAvailable()는 "없음"만 null로 완화해 줄 뿐, "모호함"은 그대로 예외로 던진다.
        withGreeters(TwoAmbiguousGreeterConfig.class, greeters ->
                assertThatThrownBy(greeters::getIfAvailable).isInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void ambiguousCandidatesGetIfUniqueReturnsNullInstead() {
        // getIfUnique()는 "없음"과 "모호함"을 둘 다 null로 완화한다 - 이름 그대로 "유일하지
        // 않으면 아무것도 안 준다".
        withGreeters(TwoAmbiguousGreeterConfig.class, greeters ->
                assertThat(greeters.getIfUnique()).isNull());
    }

    @Test
    void primaryAmongMultipleCandidatesMakesAllThreeAccessorsAgree() {
        withGreeters(TwoWithPrimaryGreeterConfig.class, greeters -> {
            // "Uniqueness ... always honors the primary flag" - @Primary가 있으면 후보가
            // 여러 개여도 애초에 모호하지 않다. 세 접근자 모두 예외 없이 같은 빈으로 수렴한다.
            assertThat(greeters.getObject().greet()).isEqualTo("Hello");
            assertThat(greeters.getIfAvailable().greet()).isEqualTo("Hello");
            assertThat(greeters.getIfUnique().greet()).isEqualTo("Hello");
        });
    }

    @Test
    void streamIsInRegistrationOrderWhileOrderedStreamRespectsAtOrder() {
        withGreeters(OrderedGreeterConfig.class, greeters -> {
            List<String> registrationOrder = greeters.stream().map(Greeter::greet).toList();
            List<String> byAtOrder = greeters.orderedStream().map(Greeter::greet).toList();

            // @Bean 메서드 선언 순서: frenchGreeter → englishGreeter → koreanGreeter
            assertThat(registrationOrder).containsExactly("Bonjour", "Hello", "안녕하세요");

            // @Order 값 순서: English(10) → Korean(20) → French(30)
            assertThat(byAtOrder).containsExactly("Hello", "안녕하세요", "Bonjour");
        });
    }

    private void withGreeters(Class<?> configClass, java.util.function.Consumer<ObjectProvider<Greeter>> assertion) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configClass)) {
            GreeterConsumer consumer = context.getBean(GreeterConsumer.class);
            assertion.accept(consumer.greeters());
        }
    }
}
