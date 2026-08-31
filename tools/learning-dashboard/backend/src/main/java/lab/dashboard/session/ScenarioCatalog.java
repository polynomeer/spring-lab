package lab.dashboard.session;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lab.dashboard.scenario.ScenarioDefinitionEntity;
import lab.dashboard.scenario.ScenarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 시나리오 이름 → 실행 가능한 정의 조회 표. docs/plan/04-dynamic-scenario-design.md 이전에는
 * 여섯 개 시나리오가 이 클래스 안에 자바 코드로 하드코딩돼 있었다 - 이제는 {@link ScenarioRepository}
 * (DB)가 그 정의를 갖고, 이 클래스는 "DB 엔티티 + 클래스패스 해석"을 {@link ScenarioSession}이
 * 바로 쓸 수 있는 {@link ScenarioDefinition}으로 조립하는 얇은 어댑터로 남는다 - 하위(세션,
 * 웹소켓 컨트롤러) 쪽 계약은 전혀 바뀌지 않는다.
 */
@Component
public class ScenarioCatalog {

    private final ScenarioRepository repository;
    private final ClasspathResolver classpathResolver;

    @Autowired
    public ScenarioCatalog(ScenarioRepository repository, ClasspathResolver classpathResolver) {
        this.repository = repository;
        this.classpathResolver = classpathResolver;
    }

    public List<String> scenarioNames() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(ScenarioDefinitionEntity::getName)
                .toList();
    }

    /** 클래스패스를 (필요하면 gradlew를 셸아웃해서) 해석해 실행 가능한 정의로 돌려준다. */
    public Optional<ScenarioDefinition> resolve(String name) {
        return repository.findByName(name).map(this::toDefinition);
    }

    private ScenarioDefinition toDefinition(ScenarioDefinitionEntity entity) {
        // 모듈을 여러 개 고른 시나리오(2단계)라면 각 모듈의 클래스패스를 이어붙인다 - 순서는
        // 엔티티에 저장된 순서(@OrderColumn) 그대로다.
        String classpath = entity.getGradleModulePaths().stream()
                .map(classpathResolver::resolve)
                .collect(Collectors.joining(File.pathSeparator));
        return new ScenarioDefinition(
                entity.getName(), classpath, entity.getMainClass(), entity.getBreakpointSpec(),
                entity.getInterpreterKind().factory());
    }
}
