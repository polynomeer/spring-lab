package lab.experiments.testcontext.probes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

// DirtiesScenarioProbeA와 완전히 같은 설정을 쓴다 - 실행 자체는 캐시에 이미 있는 컨텍스트를
// 그대로 재사용하지만, 클래스 레벨 @DirtiesContext(기본 모드 AFTER_CLASS)가 클래스의 모든
// 테스트가 끝난 뒤 그 캐시 엔트리를 폐기(close + evict)하도록 표시해 둔다.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirtiesScenarioConfig.class)
@DirtiesContext
public class DirtiesScenarioProbeDirty {

    @Test
    void justRuns() {
        assertThat(true).isTrue();
    }
}
