package lab.experiments.scheduled;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;

// period(50ms)가 실행 시간(250ms)보다 훨씬 짧다 - 그래서 매 실행이 "이전 실행 시작 시각 +
// period"라는 예정 시각을 이미 지나친 채로 끝난다. 단일 스레드 위에서는 다음 실행이 그
// 예정 시각을 더 기다리지 않고 즉시 이어진다 - 결과적으로 시작 시각 간격은 거의 실행
// 시간(250ms)에 수렴한다. FixedDelayTask와 나란히 비교한다.
public class FixedRateTask {

    private final List<Long> startTimestampsNanos = new CopyOnWriteArrayList<>();

    @Scheduled(fixedRate = 50)
    public void run() {
        startTimestampsNanos.add(System.nanoTime());
        sleepQuietly(250);
    }

    public List<Long> startTimestampsNanos() {
        return Collections.unmodifiableList(startTimestampsNanos);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
