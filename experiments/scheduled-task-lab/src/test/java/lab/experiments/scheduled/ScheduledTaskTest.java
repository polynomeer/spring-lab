package lab.experiments.scheduled;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskTest {

    @Test
    void fixedRateReschedulesFromThePreviousStartWhileFixedDelayWaitsAfterCompletion() {
        double fixedRateAvgGapMs;
        // FixedRateTask와 FixedDelayTask를 같은 컨텍스트(=같은 기본 단일 스레드, 3번째 실험
        // 참고)에 두면 두 작업이 서로의 실행 시간을 잡아먹어서 각자의 스케줄링 규칙만으로는
        // 설명되지 않는 타이밍이 나온다(직접 겪음 - 문서 8번 절 "직접 겪은 것" 참고). 그래서
        // 완전히 별도의 컨텍스트(=별도의 단일 스레드)로 분리해서 순서대로 측정한다.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(FixedRateOnlyConfig.class)) {
            FixedRateTask fixedRateTask = context.getBean(FixedRateTask.class);

            waitUntil(() -> fixedRateTask.startTimestampsNanos().size() >= 3, Duration.ofSeconds(3));

            fixedRateAvgGapMs = averageGapMillis(fixedRateTask.startTimestampsNanos());
        }

        double fixedDelayAvgGapMs;
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(FixedDelayOnlyConfig.class)) {
            FixedDelayTask fixedDelayTask = context.getBean(FixedDelayTask.class);

            waitUntil(() -> fixedDelayTask.startTimestampsNanos().size() >= 3, Duration.ofSeconds(4));

            fixedDelayAvgGapMs = averageGapMillis(fixedDelayTask.startTimestampsNanos());
        }

        // 실행 시간(250ms)이 fixedRate의 period(50ms)보다 훨씬 길다 - 매번 예정 시각을
        // 이미 지나친 채로 끝나므로, 단일 스레드는 다음 실행을 추가로 기다리지 않고 바로
        // 이어간다. 시작 시각 간격은 실행 시간(250ms)에 수렴해야 한다.
        assertThat(fixedRateAvgGapMs).isBetween(220.0, 350.0);

        // fixedDelay(250ms)는 실행이 끝난 뒤에 항상 더해진다 - 시작 시각 간격은 실행
        // 시간 + delay(약 500ms)에 수렴해야 한다.
        assertThat(fixedDelayAvgGapMs).isBetween(450.0, 650.0);

        // 가장 직접적인 비교: 같은 조건(250ms 실행)에서 fixedDelay 쪽이 확실히 더 느리다.
        assertThat(fixedDelayAvgGapMs - fixedRateAvgGapMs).isGreaterThan(150.0);
    }

    @Test
    void defaultTaskSchedulerFallsBackToASingleSharedThread() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SharedThreadConfig.class)) {
            SharedThreadTask taskA = context.getBean("taskA", SharedThreadTask.class);
            SharedThreadTask taskB = context.getBean("taskB", SharedThreadTask.class);

            waitUntil(() -> taskA.threadName() != null && taskB.threadName() != null, Duration.ofSeconds(2));

            // TaskScheduler 빈을 별도로 등록하지 않으면 ScheduledAnnotationBeanPostProcessor가
            // TaskSchedulerRouter의 로컬 폴백(Executors.newSingleThreadScheduledExecutor())으로
            // 떨어진다 - 서로 다른 두 빈의 @Scheduled 메서드가 정확히 같은 스레드에서 실행된다.
            assertThat(taskA.threadName()).isEqualTo(taskB.threadName());
        }
    }

    @Test
    void uncaughtExceptionInARepeatingTaskDoesNotCancelFutureExecutions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FlakyConfig.class)) {
            FlakyTask flakyTask = context.getBean(FlakyTask.class);

            // java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate라면 첫 예외
            // 이후 이후 실행 전체가 조용히 취소된다(자바 표준 라이브러리 Javadoc에 명시된
            // 동작) - 3번 이상 실행됐다는 것 자체가, Spring이 그 표준 동작을 그대로 쓰지
            // 않는다는 증거다.
            waitUntil(() -> flakyTask.invocationCount() >= 3, Duration.ofSeconds(2));

            assertThat(flakyTask.invocationCount()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void scheduledAnnotationIsInertWithoutEnableScheduling() throws InterruptedException {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(UnenabledConfig.class)) {
            UnenabledTask unenabledTask = context.getBean(UnenabledTask.class);

            // ScheduledAnnotationBeanPostProcessor 자체가 빈 팩토리에 없으므로, @Scheduled는
            // 그냥 읽히지 않는 메타데이터로 남는다 - 아무리 기다려도 호출되지 않는다.
            Thread.sleep(300);

            assertThat(unenabledTask.invocationCount()).isZero();
        }
    }

    private double averageGapMillis(List<Long> timestampsNanos) {
        double totalGapNanos = 0;
        int gapCount = timestampsNanos.size() - 1;
        for (int i = 1; i < timestampsNanos.size(); i++) {
            totalGapNanos += (timestampsNanos.get(i) - timestampsNanos.get(i - 1));
        }
        return (totalGapNanos / gapCount) / 1_000_000.0;
    }

    private void waitUntil(BooleanSupplier condition, Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadlineNanos) {
                throw new AssertionError("condition not met within " + timeout);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
