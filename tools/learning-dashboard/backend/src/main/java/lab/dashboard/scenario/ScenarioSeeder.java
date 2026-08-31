package lab.dashboard.scenario;

import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기존 여섯 개 시나리오를 처음 기동 시 DB에 채워 넣는다 - 이미 채워져 있으면(재기동, 또는
 * 사용자가 그사이 새 시나리오를 추가한 경우) 아무 일도 하지 않는다. 멱등이라 몇 번을 다시
 * 실행해도 안전하다.
 */
@Component
public class ScenarioSeeder implements ApplicationRunner {

    private final ScenarioRepository repository;

    public ScenarioSeeder(ScenarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Path repoRoot = ScenarioSeedData.findRepoRoot(Path.of(System.getProperty("user.dir")));
        repository.saveAll(ScenarioSeedData.defaults(repoRoot));
    }
}
