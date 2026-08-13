package lab.experiments.testcontext.probes;

import lab.experiments.testcontext.ContextCreationCounter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ConfigX와 구조는 완전히 같지만(같은 빈 하나), 클래스 자체가 다르다 - 그 사실 하나만으로
// MergedContextConfiguration의 캐시 키가 달라진다.
@Configuration
public class ConfigY {

    @Bean
    public ContextCreationCounter contextCreationCounter() {
        return new ContextCreationCounter();
    }
}
