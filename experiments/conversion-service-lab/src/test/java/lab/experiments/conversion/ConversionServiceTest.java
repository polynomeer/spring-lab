package lab.experiments.conversion;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionServiceTest {

    @Test
    void correctlyNamedConversionServiceBeanEnablesCustomTypeConversionAndBuiltInCollectionSplitting() {
        try (AnnotationConfigApplicationContext context =
                     contextWithProperties(CorrectlyNamedConversionServiceConfig.class)) {
            ConvertiblePropertiesHolder holder = context.getBean(ConvertiblePropertiesHolder.class);

            // Point는 커스텀 Converter(StringToPointConverter)의 도움 없이는 절대 만들어질
            // 수 없는 타입이다 - 여기서 성공한다는 것 자체가, 이 커스텀 컨버터가 실제로
            // @Value 처리 경로에 연결됐다는 증거다.
            assertThat(holder.point()).isEqualTo(new Point(3, 4));

            // List<Integer>는 커스텀 컨버터를 하나도 안 만들었는데도 변환된다 -
            // DefaultConversionService가 기본으로 갖고 있는 문자열→컬렉션 분리 변환기
            // 덕분이다.
            assertThat(holder.numbers()).isEqualTo(List.of(1, 2, 3));
        }
    }

    @Test
    void wronglyNamedConversionServiceBeanIsNeverConsultedByTheContainer() {
        // 타입은 완전히 같은 ConversionService인데, 빈 이름이 "conversionService"가 아니다 -
        // AbstractApplicationContext#prepareBeanFactory()는 그 리터럴 이름 하나만 찾아보고,
        // 없으면(=이름이 다르면) beanFactory.setConversionService()를 아예 호출하지 않는다.
        assertThatThrownBy(() -> {
            try (AnnotationConfigApplicationContext context =
                         contextWithProperties(WronglyNamedConversionServiceConfig.class)) {
                context.getBean(ConvertiblePropertiesHolder.class);
            }
        })
                .isInstanceOf(UnsatisfiedDependencyException.class)
                .rootCause()
                .hasMessageContaining("no matching editors or conversion strategy found");
    }

    @Test
    void missingAndWronglyNamedConversionServiceProduceTheExactSameFailure() {
        // 이름이 다른 ConversionService 빈은 "덜 도움이 되는" 정도가 아니라, 컨테이너
        // 입장에서는 그 빈이 존재하지 않는 것과 완전히 동일하게 취급된다 - 실패 메시지가
        // 글자 그대로 같다는 것으로 증명한다.
        String wronglyNamedFailure = captureRootCauseMessage(WronglyNamedConversionServiceConfig.class);
        String missingFailure = captureRootCauseMessage(NoConversionServiceConfig.class);

        assertThat(wronglyNamedFailure).isEqualTo(missingFailure);
    }

    private String captureRootCauseMessage(Class<?> configClass) {
        try (AnnotationConfigApplicationContext context = contextWithProperties(configClass)) {
            context.getBean(ConvertiblePropertiesHolder.class);
            throw new AssertionError("expected context refresh to fail for " + configClass);
        } catch (UnsatisfiedDependencyException ex) {
            Throwable rootCause = ex;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            return rootCause.getMessage();
        }
    }

    private AnnotationConfigApplicationContext contextWithProperties(Class<?> configClass) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("app.point", "3,4", "app.numbers", "1,2,3")));
        context.register(configClass);
        context.refresh();
        return context;
    }
}
