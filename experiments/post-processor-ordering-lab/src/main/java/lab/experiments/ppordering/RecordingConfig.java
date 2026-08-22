package lab.experiments.ppordering;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Bean 메서드 선언 순서를 일부러 최종 실행 순서와 어긋나게 배치했다 - 만약 실행
// 결과가 "선언 순서" 또는 "order 값 하나로 전체 정렬"이 아니라 "그룹별로 나뉘고,
// 그룹 안에서만 order 값으로 정렬"이라는 걸 실제로 재현한다면, 이 뒤섞인 선언
// 순서에도 불구하고 그룹 우선순위가 정확히 지켜져야 한다.
@Configuration
public class RecordingConfig {

    @Bean
    public List<String> orderLog() {
        return new ArrayList<>();
    }

    @Bean
    public Target target() {
        return new Target();
    }

    @Bean
    public PlainOrderAnnotatedSecondBpp plainSecond(List<String> orderLog) {
        return new PlainOrderAnnotatedSecondBpp(orderLog);
    }

    @Bean
    public OrderedBpp orderedLow(List<String> orderLog) {
        return new OrderedBpp("ordered(order=MIN_VALUE)", Integer.MIN_VALUE, orderLog);
    }

    @Bean
    public PriorityOrderedBpp priorityOrderedHigh(List<String> orderLog) {
        return new PriorityOrderedBpp("priorityOrdered(order=MAX_VALUE)", Integer.MAX_VALUE, orderLog);
    }

    @Bean
    public PlainOrderAnnotatedFirstBpp plainFirst(List<String> orderLog) {
        return new PlainOrderAnnotatedFirstBpp(orderLog);
    }

    @Bean
    public PriorityOrderedBpp priorityOrderedLow(List<String> orderLog) {
        return new PriorityOrderedBpp("priorityOrdered(order=MIN_VALUE)", Integer.MIN_VALUE, orderLog);
    }
}
