package lab.experiments.scheduled;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;

// 매 실행이 예외를 던진다 - java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate
// 라면 첫 예외 이후 이후 실행 전체가 조용히 취소되지만(자바 표준 라이브러리 자체의 동작),
// Spring의 @Scheduled가 그것과 같은지 다른지를 실행으로 직접 확인하기 위한 용도.
public class FlakyTask {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Scheduled(fixedRate = 50)
    public void run() {
        invocationCount.incrementAndGet();
        throw new IllegalStateException("이번 실행은 항상 실패한다");
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
