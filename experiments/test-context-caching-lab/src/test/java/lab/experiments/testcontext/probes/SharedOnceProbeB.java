package lab.experiments.testcontext.probes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

// SharedOnceProbeA와 완전히 같은 @ContextConfiguration(classes = SharedOnceConfig.class)를
// 쓰는, 별도의 클래스 - 별도의 TestContextManager를 갖지만 같은 MergedContextConfiguration으로
// 귀결된다.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SharedOnceConfig.class)
public class SharedOnceProbeB {

    @Test
    void justRuns() {
        assertThat(true).isTrue();
    }
}
