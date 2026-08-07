package lab.dashboard.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCatalogTest {

    // scenarioNames()는 spec 파일을 열지 않으므로 repoRoot 값 자체는 이 테스트에서 중요하지
    // 않다 - resolve()(gradlew 셸아웃)를 부르지 않는 한 ClasspathResolver도 실제로 쓰이지 않는다.
    private final ScenarioCatalog catalog = new ScenarioCatalog(Path.of("."), new ClasspathResolver());

    @Test
    void listsAllSixScenariosIncludingTheNowLiveMvcExceptionPriority() {
        assertThat(catalog.scenarioNames()).containsExactly(
                "bean-lifecycle", "aop-proxy", "tx-propagation", "dispatcher-flow", "event-multicast", "mvc-exception-priority");
    }
}
