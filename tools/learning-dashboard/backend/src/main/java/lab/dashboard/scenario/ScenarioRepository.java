package lab.dashboard.scenario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<ScenarioDefinitionEntity, Long> {

    Optional<ScenarioDefinitionEntity> findByName(String name);

    boolean existsByName(String name);

    // 등록 순서를 보존한다 - 기존 ScenarioCatalog가 LinkedHashMap으로 유지하던 순서(시드
    // 데이터 순서 -> 사용자가 새로 추가한 순서)를 그대로 이어받는다. createdAt이 아니라 id로
    // 정렬하는 이유: saveAll()로 여러 엔티티를 한 번에 시드할 때 @PrePersist의 Instant.now()가
    // 같은 밀리초로 찍혀 순서가 불안정해질 수 있지만, IDENTITY 자동증가 id는 삽입 순서를
    // 항상 정확히 보존한다.
    List<ScenarioDefinitionEntity> findAllByOrderByIdAsc();
}
