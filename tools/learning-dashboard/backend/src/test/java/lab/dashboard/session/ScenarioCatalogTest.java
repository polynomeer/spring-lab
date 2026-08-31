package lab.dashboard.session;

import java.nio.file.Path;

import lab.dashboard.scenario.DynamicScenarioCompiler;
import lab.dashboard.scenario.ScenarioRepository;
import lab.dashboard.scenario.ScenarioSeedData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest(리포지토리 슬라이스) - 실제 H2 인메모리 DB 위에서 ScenarioRepository를
// 진짜로 굴려서 확인한다. resolve()(gradlew 셸아웃)는 여전히 여기서 부르지 않는다 -
// scenarioNames()는 ClasspathResolver를 전혀 쓰지 않으므로 이 테스트만으로 충분하다.
@DataJpaTest
class ScenarioCatalogTest {

    @Autowired
    private ScenarioRepository repository;

    @Test
    void listsAllSixSeededScenariosInRegistrationOrder() {
        Path repoRoot = ScenarioSeedData.findRepoRoot(Path.of(System.getProperty("user.dir")));
        repository.saveAll(ScenarioSeedData.defaults(repoRoot));

        ScenarioCatalog catalog = new ScenarioCatalog(repository, new ClasspathResolver(), new DynamicScenarioCompiler());

        assertThat(catalog.scenarioNames()).containsExactly(
                "bean-lifecycle", "aop-proxy", "tx-propagation", "dispatcher-flow", "event-multicast",
                "mvc-exception-priority");
    }
}
