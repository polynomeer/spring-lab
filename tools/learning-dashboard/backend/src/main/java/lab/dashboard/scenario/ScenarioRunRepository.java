package lab.dashboard.scenario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRunRepository extends JpaRepository<ScenarioRunEntity, Long> {

    List<ScenarioRunEntity> findByScenarioNameOrderByStartedAtDesc(String scenarioName);
}
