package lab.experiments.scheduled;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;

// delay(250ms)는 "이전 실행이 끝난 뒤" 기다리는 시간이다 - 실행 시간(250ms)과는 무관하게
// 매번 더해진다. 그래서 시작 시각 간격은 실행 시간 + delay(약 500ms)에 수렴한다.
public class FixedDelayTask {

    private final List<Long> startTimestampsNanos = new CopyOnWriteArrayList<>();

    @Scheduled(fixedDelay = 250)
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
