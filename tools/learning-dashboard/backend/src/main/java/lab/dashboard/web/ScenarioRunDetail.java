package lab.dashboard.web;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code GET /api/scenario-runs/{id}}가 돌려주는 실행 기록 하나 - {@code events}는
 * {@code ScenarioMessageMapper}가 만드는 것과 정확히 같은 봉투 배열이라, 프론트엔드가
 * 실시간 STOMP 스트림과 똑같은 {@code ScenarioMessage[]}로 그대로 받아 기존 컴포넌트
 * (RawEventLog/SemanticEventLog)에 넘길 수 있다 - 재생 전용 프론트엔드 코드가 필요 없다.
 */
public record ScenarioRunDetail(
        Long id,
        String scenarioName,
        Instant startedAt,
        Instant finishedAt,
        Integer totalHits,
        boolean timedOut,
        List<JsonNode> events) {
}
