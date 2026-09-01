package lab.dashboard.web;

import java.time.Instant;

import lab.dashboard.scenario.ScenarioRunEntity;

/** {@code GET /api/scenario-runs}의 목록 항목 - 전체 이벤트 배열은 안 담는다(목록이 커질
 * 수 있으니, 그건 {@link ScenarioRunDetail}에서 id로 따로 조회한다). */
public record ScenarioRunSummary(
        Long id,
        String scenarioName,
        Instant startedAt,
        Instant finishedAt,
        Integer totalHits,
        boolean timedOut) {

    public static ScenarioRunSummary from(ScenarioRunEntity entity) {
        return new ScenarioRunSummary(
                entity.getId(), entity.getScenarioName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getTotalHits(), entity.isTimedOut());
    }
}
