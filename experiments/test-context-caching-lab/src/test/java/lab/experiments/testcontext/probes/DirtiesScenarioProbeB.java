package lab.experiments.testcontext.probes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

// 역시 같은 설정 - DirtiesScenarioProbeDirty가 캐시를 이미 폐기해 둔 뒤에 실행되면, 여기서는
// 캐시 미스가 나서 ApplicationContext가 다시 새로 만들어져야 한다.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirtiesScenarioConfig.class)
public class DirtiesScenarioProbeB {

    @Test
    void justRuns() {
        assertThat(true).isTrue();
    }
}
