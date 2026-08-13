package lab.experiments.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.springframework.scheduling.annotation.Async;

public class AsyncTaskServiceImpl implements AsyncTaskService {

    @Override
    @Async
    public CompletableFuture<String> recordThreadName() {
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    @Override
    @Async
    public void fireAndForgetThatThrows() {
        throw new IllegalStateException("fire-and-forget 호출은 항상 실패한다");
    }

    @Override
    @Async
    public Future<String> returnsFutureThatThrows() {
        throw new IllegalStateException("Future 반환 호출은 항상 실패한다");
    }

    @Override
    public String refreshViaSelfInvocation() {
        // this.recordThreadName()는 프록시가 아니라 대상 객체를 직접 호출한다 - @Async가
        // 적용되지 않아 메서드 본문이 호출 스레드 위에서 그대로(동기적으로) 실행되고, 이미
        // 완료된 CompletableFuture를 반환한다.
        return this.recordThreadName().join();
    }
}
