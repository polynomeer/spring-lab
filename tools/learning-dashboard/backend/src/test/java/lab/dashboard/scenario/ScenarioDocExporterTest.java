package lab.dashboard.scenario;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDocExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScenarioDocExporter exporter = new ScenarioDocExporter(mapper);

    private ScenarioDefinitionEntity scenario() {
        return new ScenarioDefinitionEntity(
                "export-test", "내보내기 테스트", "설명",
                List.of("experiments:ioc-container-lab"), "lab.experiments.ioc.BeanFactoryLab",
                "org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean",
                InterpreterKind.NONE);
    }

    @Test
    void withoutARunTheObservationSectionIsLeftAsATodo() {
        String markdown = exporter.export(scenario(), null);

        assertThat(markdown).contains("## 8. 런타임 관찰");
        assertThat(markdown).contains("실행 기록을 남긴 뒤");
        assertThat(markdown).doesNotContain("| # | 이벤트 |");
    }

    @Test
    void withACompletedRunTheObservationTableIsBuiltFromItsSemanticEvents() throws Exception {
        String eventsJson = mapper.writeValueAsString(List.of(
                Map.of("type", "hit", "scenario", "export-test", "event", Map.of("hitId", 1)),
                Map.of("type", "semantic", "scenario", "export-test",
                        "event", Map.of("type", "bean-created", "sourceHitId", 1,
                                "attributes", Map.of("beanName", "myBean")))));
        ScenarioRunEntity run = new ScenarioRunEntity(
                "export-test", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"),
                1, false, eventsJson);

        String markdown = exporter.export(scenario(), run);

        assertThat(markdown).contains("| # | 이벤트 | 원본 히트 | 속성 |");
        assertThat(markdown).contains("bean-created");
        assertThat(markdown).contains("beanName=myBean");
    }
}
