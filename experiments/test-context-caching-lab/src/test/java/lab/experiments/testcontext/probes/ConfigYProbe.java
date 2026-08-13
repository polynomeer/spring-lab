package lab.experiments.testcontext.probes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ConfigY.class)
public class ConfigYProbe {

    @Test
    void justRuns() {
        assertThat(true).isTrue();
    }
}
