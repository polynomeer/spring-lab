package lab.experiments.ppordering;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PostProcessorOrderingTest {

    /**
     * RecordingConfig는 @Bean 메서드를 일부러 최종 실행 순서와 어긋나게 선언해 둔다
     * (plainSecond -> orderedLow -> priorityOrderedHigh -> plainFirst -> priorityOrderedLow).
     * 그런데도 registerBeanPostProcessors()가 만드는 실제 실행 순서는 선언 순서도,
     * order 값 하나로 통째 정렬한 순서도 아니라 - "그룹(PriorityOrdered -> Ordered ->
     * 나머지)이 먼저 결정되고, order 값은 오직 같은 그룹 안에서만 정렬에 쓰인다."
     */
    @Test
    void groupMembershipAlwaysBeatsNumericOrderValueAcrossBucketBoundaries() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RecordingConfig.class)) {
            @SuppressWarnings("unchecked")
            List<String> log = context.getBean("orderLog", List.class);

            assertThat(log).containsExactly(
                    // PriorityOrdered 버킷 - 버킷 안에서는 order 값으로 정렬됨(MIN_VALUE가 먼저)
                    "priorityOrdered(order=MIN_VALUE)",
                    "priorityOrdered(order=MAX_VALUE)",
                    // Ordered 버킷 - order=MIN_VALUE라서 숫자로는 가장 앞서지만,
                    // PriorityOrdered 버킷 전체보다 항상 나중이다(그룹 경계를 못 넘음)
                    "ordered(order=MIN_VALUE)",
                    // "나머지" 버킷 - @Order 애너테이션 값과 무관하게 등록(선언) 순서 그대로:
                    // plainSecond가 plainFirst보다 먼저 선언됐으므로 @Order 값(MAX_VALUE)이
                    // 더 커도(=우선순위가 낮아도) 먼저 실행된다
                    "plain(@Order=MAX_VALUE, bean method 순서상 두 번째)",
                    "plain(@Order=MIN_VALUE, bean method 순서상 첫 번째)");
        }
    }
}
