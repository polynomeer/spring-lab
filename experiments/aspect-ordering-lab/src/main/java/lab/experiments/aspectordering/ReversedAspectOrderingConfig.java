package lab.experiments.aspectordering;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// AspectOrderingConfig와 완전히 같은 세 개의 어드바이스지만, order 값을 뒤집어서
// (A=3, B=2, C=1) 등록한다 - 중첩 순서가 값을 따라 그대로 뒤집히는지 확인한다.
@Configuration
@EnableAspectJAutoProxy
public class ReversedAspectOrderingConfig {

    @Bean
    public List<String> log() {
        return new ArrayList<>();
    }

    @Bean
    public Greeter greeter() {
        return new GreeterImpl();
    }

    @Bean
    public OrderedLoggingAspect aspectA(List<String> log) {
        return new OrderedLoggingAspect("A", 3, log);
    }

    @Bean
    public OrderedLoggingAspect aspectB(List<String> log) {
        return new OrderedLoggingAspect("B", 2, log);
    }

    @Bean
    public OrderedLoggingAspect aspectC(List<String> log) {
        return new OrderedLoggingAspect("C", 1, log);
    }
}
