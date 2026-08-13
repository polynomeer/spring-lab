package lab.experiments.scheduled;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// FixedRateTask와 FixedDelayTask를 같은 컨텍스트(=같은 기본 단일 스레드)에 두면 두 작업이
// 서로의 실행 시간을 잡아먹어서 각자의 스케줄링 규칙만으로는 설명되지 않는 타이밍이 나온다
// (직접 겪음 - 8번 절 "직접 겪은 것" 참고). 그래서 각 모드를 완전히 별도의 컨텍스트로
// 분리했다.
@Configuration
@EnableScheduling
public class FixedRateOnlyConfig {

    @Bean
    public FixedRateTask fixedRateTask() {
        return new FixedRateTask();
    }
}
