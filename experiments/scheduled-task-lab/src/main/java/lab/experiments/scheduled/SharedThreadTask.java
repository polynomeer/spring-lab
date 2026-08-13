package lab.experiments.scheduled;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;

// 첫 실행이 어느 스레드에서 일어났는지만 기록한다 - TaskScheduler 빈을 따로 등록하지
// 않으면 ScheduledAnnotationBeanPostProcessor가 기본값(TaskSchedulerRouter의 로컬
// 폴백)으로 단일 스레드 ScheduledExecutorService를 만든다는 것을 확인하기 위한 용도.
public class SharedThreadTask {

    private final AtomicReference<String> threadName = new AtomicReference<>();

    @Scheduled(fixedDelay = 10_000)
    public void run() {
        threadName.compareAndSet(null, Thread.currentThread().getName());
    }

    public String threadName() {
        return threadName.get();
    }
}
