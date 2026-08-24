package lab.experiments.aspectordering;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// @Bean 메서드를 일부러 order 값과 어긋나는 순서(C, A, B)로 선언해 뒀다 - 만약
// 최종 실행 순서가 order 값과 정확히 일치한다면, 그건 등록 순서가 아니라 order
// 값 자체가 advisor 정렬을 결정한다는 증거다.
@Configuration
@EnableAspectJAutoProxy
public class AspectOrderingConfig {

    @Bean
    public List<String> log() {
        return new ArrayList<>();
    }

    @Bean
    public Greeter greeter() {
        return new GreeterImpl();
    }

    @Bean
    public OrderedLoggingAspect aspectC(List<String> log) {
        return new OrderedLoggingAspect("C", 3, log);
    }

    @Bean
    public OrderedLoggingAspect aspectA(List<String> log) {
        return new OrderedLoggingAspect("A", 1, log);
    }

    @Bean
    public OrderedLoggingAspect aspectB(List<String> log) {
        return new OrderedLoggingAspect("B", 2, log);
    }
}
