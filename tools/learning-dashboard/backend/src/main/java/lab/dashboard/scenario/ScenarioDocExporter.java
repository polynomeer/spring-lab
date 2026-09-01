package lab.dashboard.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * 시나리오 하나(+선택적으로 완료된 실행 기록 하나)를 docs/plan/00-methodology.md
 * "주제별 문서 템플릿"(12절 골격) 마크다운으로 내보낸다 -
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "시나리오 → 정식 문서 뼈대 export".
 *
 * <p>이 문서 자체가 "템플릿 자동 채움의 품질이 낮으면 손으로 쓰는 것보다 손이 더 갈
 * 위험"을 명시적으로 지적했다 - 그래서 자동으로 채우는 절은 딱 두 곳으로 제한한다:
 * 4절(최소 재현 코드 - 실제 소스/모듈 참조를 그대로 옮기는 것뿐)과 8절(런타임 관찰 -
 * 이미 기록된 실행의 semantic 이벤트를 표로 바꾸는 것뿐, 둘 다 "새로 지어내는" 게 아니라
 * "이미 있는 사실을 옮기는" 기계적 작업이다). 해석/분석이 필요한 나머지 절(2, 3, 9, 10,
 * 11, 12)은 절대 그럴듯한 문장을 지어내지 않고 명확한 TODO로 남겨, 사용자가 "이건 내가
 * 쓴 게 아니라 도구가 채운 것"을 한눈에 구분할 수 있게 한다.
 */
@Component
public class ScenarioDocExporter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private final ObjectMapper mapper;

    public ScenarioDocExporter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String export(ScenarioDefinitionEntity scenario, ScenarioRunEntity run) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(scenario.getTitle()).append("\n\n");
        if (scenario.getDescription() != null && !scenario.getDescription().isBlank()) {
            md.append(scenario.getDescription()).append("\n\n");
        }

        section(md, "1. 이번 질문", "<!-- TODO: 이 시나리오로 무엇을 확인하려 했는지 적어 주세요 -->");
        section(md, "2. 공식 문서 요약", "<!-- TODO: 관련 공식 문서를 요약해 주세요 -->");
        section(md, "3. 예상 동작 (소스를 보기 전에 작성)", "<!-- TODO: 소스를 보기 전에 예상한 동작을 적어 주세요 -->");

        md.append("## 4. 최소 재현 코드\n\n");
        if (scenario.getSourceCode() != null && !scenario.getSourceCode().isBlank()) {
            md.append("```java\n").append(scenario.getSourceCode()).append("\n```\n\n");
        } else {
            md.append("- 모듈: `").append(String.join("`, `", scenario.getGradleModulePaths())).append("`\n");
            md.append("- 클래스: `").append(scenario.getMainClass()).append("`\n\n");
        }

        section(md, "5. 핵심 타입 (인터페이스 / 추상 클래스 / 대표 구현체 / 전략 인터페이스)",
                "<!-- TODO: | 타입 | 책임 | 형태의 표를 채워 주세요 -->");
        section(md, "6. 호출 흐름", "<!-- TODO: 호출 순서를 적어 주세요 -->");

        md.append("## 7. 브레이크포인트\n\n```text\n").append(scenario.getBreakpointSpec()).append("\n```\n\n");

        md.append("## 8. 런타임 관찰 (객체 타입 / 프록시 여부 / 빈 상태 / ThreadLocal 상태 / 호출 순서)\n\n");
        if (run != null) {
            appendObservationTable(md, run);
        } else {
            md.append("<!-- TODO: 시나리오를 끝까지 재생해 실행 기록을 남긴 뒤, ")
                    .append("그 실행으로 다시 내보내면 이 표가 자동으로 채워집니다 -->\n\n");
        }

        section(md, "9. 공식 테스트 분석", "<!-- TODO: 관련 공식 테스트를 분석해 주세요 -->");
        section(md, "10. 축소 구현 (구현한 것 / 생략한 것)", "<!-- TODO: 무엇을 구현했고 무엇을 생략했는지 적어 주세요 -->");
        section(md, "11. Spring 설계 의도", "<!-- TODO: 이렇게 설계된 이유를 적어 주세요 -->");
        section(md, "12. 결론 (예상과 실제의 차이)", "<!-- TODO: 3절의 예상과 8절의 관찰이 어떻게 달랐는지 적어 주세요 -->");

        md.append("<!-- learning-dashboard 시나리오 '").append(scenario.getName())
                .append("'에서 자동 생성된 뼈대입니다. TODO로 표시된 절은 도구가 채운 것이 아니라 직접 채워야 합니다. -->\n");
        return md.toString();
    }

    private void section(StringBuilder md, String heading, String placeholder) {
        md.append("## ").append(heading).append("\n\n").append(placeholder).append("\n\n");
    }

    private void appendObservationTable(StringBuilder md, ScenarioRunEntity run) {
        md.append("실행 시각: ").append(TIMESTAMP.format(run.getStartedAt()))
                .append(" · 총 히트 ").append(run.isTimedOut() ? "타임아웃" : run.getTotalHits()).append("\n\n");

        Iterator<JsonNode> semanticEvents = parseSemanticEvents(run.getEventsJson());
        if (!semanticEvents.hasNext()) {
            md.append("<!-- TODO: 이 실행에는 semantic 이벤트가 없습니다 - 직접 관찰 내용을 적어 주세요 -->\n\n");
            return;
        }

        md.append("| # | 이벤트 | 원본 히트 | 속성 |\n|---|---|---|---|\n");
        int index = 1;
        while (semanticEvents.hasNext()) {
            JsonNode envelope = semanticEvents.next();
            JsonNode event = envelope.path("event");
            md.append("| ").append(index++).append(" | ")
                    .append(event.path("type").asText("?")).append(" | ")
                    .append(event.path("sourceHitId").asText("?")).append(" | ")
                    .append(formatAttributes(event.path("attributes"))).append(" |\n");
        }
        md.append("\n");
    }

    private String formatAttributes(JsonNode attributes) {
        if (!attributes.isObject() || attributes.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        attributes.properties().forEach(entry -> {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(entry.getKey()).append('=').append(entry.getValue().asText());
        });
        return out.toString();
    }

    private Iterator<JsonNode> parseSemanticEvents(String eventsJson) {
        try {
            JsonNode array = mapper.readTree(eventsJson);
            java.util.List<JsonNode> semantic = new java.util.ArrayList<>();
            array.forEach(node -> {
                if ("semantic".equals(node.path("type").asText())) {
                    semantic.add(node);
                }
            });
            return semantic.iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
