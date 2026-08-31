package lab.dashboard.web;

import java.time.Instant;
import java.util.List;

import lab.dashboard.scenario.ScenarioDefinitionEntity;

/** {@code GET /api/scenarios}가 돌려주는 저장된 시나리오 하나의 모양. */
public record ScenarioResponse(
        Long id,
        String name,
        String title,
        String description,
        List<String> gradleModulePaths,
        String mainClass,
        String breakpointSpec,
        String sourceCode,
        String interpreterKind,
        Instant createdAt) {

    public static ScenarioResponse from(ScenarioDefinitionEntity entity) {
        return new ScenarioResponse(
                entity.getId(),
                entity.getName(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getGradleModulePaths(),
                entity.getMainClass(),
                entity.getBreakpointSpec(),
                entity.getSourceCode(),
                entity.getInterpreterKind().name(),
                entity.getCreatedAt());
    }
}
