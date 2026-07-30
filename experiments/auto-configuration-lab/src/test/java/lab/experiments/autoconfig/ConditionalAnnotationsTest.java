package lab.experiments.autoconfig;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionalAnnotationsTest {

    private AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(PlainAppConfig.class);
        context.refresh();
        return context;
    }

    @Test
    void conditionalOnClassActivatesWhenTheClassIsOnTheClasspath() {
        // 이 테스트 실행 시점의 클래스패스에는 jackson-databind가 testImplementation으로
        // 올라와 있다 - 그래서 문자열 이름으로만 지정한 @ConditionalOnClass가 실제로 매치된다.
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of())) {
            assertThat(context.getBean(JacksonProbe.class)).isNotNull();
        }
    }

    @Test
    void conditionalOnPropertyDisablesTheAutoConfigurationWhenSetToFalse() {
        try (AnnotationConfigApplicationContext context =
                contextWithProperties(Map.of("greeting.enabled", "false"))) {
            assertThatThrownBy(() -> context.getBean(GreetingService.class))
                    .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    void conditionalOnPropertyMatchIfMissingKeepsItEnabledByDefault() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of())) {
            assertThat(context.getBean(GreetingService.class)).isNotNull();
        }
    }

    @Test
    void customConditionActivatesOnlyWhenItsPropertyIsExactlyTrue() {
        try (AnnotationConfigApplicationContext offContext = contextWithProperties(Map.of())) {
            assertThatThrownBy(() -> offContext.getBean(SlowModeMarker.class))
                    .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
        }

        try (AnnotationConfigApplicationContext onContext =
                contextWithProperties(Map.of("lab.slow-mode", "true"))) {
            assertThat(onContext.getBean(SlowModeMarker.class)).isNotNull();
        }
    }

    @Test
    void conditionEvaluationReportExplainsWhyEachCandidateMatchedOrNot() {
        try (AnnotationConfigApplicationContext context =
                contextWithProperties(Map.of("greeting.enabled", "false"))) {
            ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());

            String greetingKey = findKeyEndingWith(report, "GreetingAutoConfiguration");
            String slowModeKey = findKeyEndingWith(report, "SlowModeAutoConfiguration");

            boolean greetingMatched = report.getConditionAndOutcomesBySource().get(greetingKey).isFullMatch();
            boolean slowModeMatched = report.getConditionAndOutcomesBySource().get(slowModeKey).isFullMatch();

            assertThat(greetingMatched).isFalse();
            assertThat(slowModeMatched).isFalse();

            String greetingReasons = StreamSupport
                    .stream(report.getConditionAndOutcomesBySource().get(greetingKey).spliterator(), false)
                    .map(conditionAndOutcome -> conditionAndOutcome.getOutcome().getMessage())
                    .collect(Collectors.joining("; "));
            assertThat(greetingReasons).contains("greeting.enabled");
        }
    }

    private String findKeyEndingWith(ConditionEvaluationReport report, String suffix) {
        return report.getConditionAndOutcomesBySource().keySet().stream()
                .filter(key -> key.endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no condition report entry ending with " + suffix));
    }
}
