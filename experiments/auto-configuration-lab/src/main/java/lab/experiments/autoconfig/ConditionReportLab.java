package lab.experiments.autoconfig;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

/**
 * 대시보드 condition-report 시나리오의 진입점 - 다른 Lab들과 달리 JDI로 스텝을 밟지 않는다.
 * {@code ConditionEvaluationReport}는 {@code refresh()}가 끝나는 순간 이미 완성된 데이터라
 * "단계"가 없다(docs/plan/03-learning-dashboard-design.md 6.5절 참고) - 그래서 이 클래스는
 * 컨텍스트를 한 번 띄우고, 리포트를 조회해서 JSON 한 줄을 찍고, 바로 끝난다.
 *
 * <p>이 모듈의 {@code build.gradle.kts}는 Jackson을 일부러 {@code testImplementation}으로만
 * 넣어 뒀다 - {@code JacksonSupportAutoConfiguration}의 {@code @ConditionalOnClass(name =
 * "...ObjectMapper")}가 "런타임 클래스패스엔 없다"를 보여주기 위한 장치라, 여기서 Jackson을
 * 끌어와 직렬화하면 그 실험 자체가 망가진다. 그래서 JSON은 직접 문자열로 만든다 - 리포트
 * 구조가 고정돼 있어 어렵지 않다.
 *
 * <p>인자는 {@code key=value} 형태의 프로퍼티 오버라이드다(예: {@code greeting.enabled=false}) -
 * 대시보드의 "다시 실행" 프리셋이 이 인자로 넘어온다.
 *
 * <p>{@code @EnableAutoConfiguration}은 클래스패스의 {@code spring-boot-autoconfigure} 전체
 * ({@code AopAutoConfiguration}, {@code RabbitAutoConfiguration} 등 100개 넘는 실제 Boot
 * 자동 설정)를 함께 주워 담는다 - 실제로 직접 확인했다. 리포트를 지어내는 건 아니지만, 이
 * 저장소가 직접 만든 4개짜리 조건 예시를 보여주려는 목적에는 순전히 잡음이라, 소스 이름이
 * {@value #LAB_PACKAGE_PREFIX}로 시작하는 것만 걸러서 출력한다.
 */
public final class ConditionReportLab {

    private static final String LAB_PACKAGE_PREFIX = "lab.experiments.autoconfig";

    @Configuration
    @EnableAutoConfiguration
    static class AppConfig {
    }

    private ConditionReportLab() {
    }

    public static void main(String[] args) {
        Map<String, Object> overrides = new TreeMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq > 0) {
                overrides.put(arg.substring(0, eq), arg.substring(eq + 1));
            }
        }

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            if (!overrides.isEmpty()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
            }
            context.register(AppConfig.class);
            context.refresh();

            ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
            System.out.println(toJson(report));
        }
    }

    private static String toJson(ConditionEvaluationReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\"sources\":{");
        boolean firstSource = true;
        for (Map.Entry<String, ConditionEvaluationReport.ConditionAndOutcomes> entry :
                report.getConditionAndOutcomesBySource().entrySet()) {
            if (!entry.getKey().startsWith(LAB_PACKAGE_PREFIX)) continue;
            if (!firstSource) json.append(',');
            firstSource = false;
            json.append(jsonString(entry.getKey())).append(':');
            appendSource(json, entry.getValue());
        }
        json.append("},\"unconditional\":[");
        boolean firstUnconditional = true;
        for (String className : report.getUnconditionalClasses()) {
            if (!className.startsWith(LAB_PACKAGE_PREFIX)) continue;
            if (!firstUnconditional) json.append(',');
            firstUnconditional = false;
            json.append(jsonString(className));
        }
        json.append("]}");
        return json.toString();
    }

    private static void appendSource(StringBuilder json, ConditionEvaluationReport.ConditionAndOutcomes outcomes) {
        json.append("{\"fullMatch\":").append(outcomes.isFullMatch()).append(",\"outcomes\":[");
        boolean first = true;
        for (ConditionEvaluationReport.ConditionAndOutcome outcome : outcomes) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"condition\":").append(jsonString(outcome.getCondition().getClass().getSimpleName()))
                    .append(",\"matched\":").append(outcome.getOutcome().isMatch())
                    .append(",\"message\":").append(jsonString(outcome.getOutcome().getMessage()))
                    .append('}');
        }
        json.append("]}");
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
