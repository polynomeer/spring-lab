package lab.experiments.scheduled;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;

// @Scheduled 자체는 아무 마커 애노테이션도 아니다 - ScheduledAnnotationBeanPostProcessor가
// 빈 팩토리에 등록돼 있어야만(즉 @EnableScheduling이 있어야만) 이 애노테이션을 읽는 주체가
// 존재한다. UnenabledConfig에는 그게 없다 - 대조군.
public class UnenabledTask {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Scheduled(fixedRate = 50)
    public void run() {
        invocationCount.incrementAndGet();
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
