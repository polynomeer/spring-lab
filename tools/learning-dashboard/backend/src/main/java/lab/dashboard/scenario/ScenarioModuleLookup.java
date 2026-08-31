package lab.dashboard.scenario;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

/** {@link GradleModuleCatalog}(패키지 전용 파서)를 저장소 루트에 고정해 감싸는 빈. */
@Component
public class ScenarioModuleLookup {

    private final Path repoRoot;

    public ScenarioModuleLookup() {
        this.repoRoot = ScenarioSeedData.findRepoRoot(Path.of(System.getProperty("user.dir")));
    }

    ScenarioModuleLookup(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public List<String> listModulePaths() {
        return GradleModuleCatalog.listModulePaths(repoRoot);
    }
}
