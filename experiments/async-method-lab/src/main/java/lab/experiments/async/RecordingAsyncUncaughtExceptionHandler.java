package lab.experiments.async;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

// void를 반환하는 @Async 메서드가 던진 예외는 호출자에게 전달할 방법이 없다 - 그 예외가
// 실제로 여기까지 도달했는지를 계측하기 위한 용도(비동기라서 CountDownLatch로 기다려야 한다).
public class RecordingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile Throwable lastException;
    private volatile String lastMethodName;

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        this.lastException = ex;
        this.lastMethodName = method.getName();
        this.latch.countDown();
    }

    public boolean awaitHandled(long timeoutMillis) throws InterruptedException {
        return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public Throwable lastException() {
        return lastException;
    }

    public String lastMethodName() {
        return lastMethodName;
    }
}
