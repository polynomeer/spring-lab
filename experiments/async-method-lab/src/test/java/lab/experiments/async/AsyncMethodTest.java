package lab.experiments.async;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncMethodTest {

    @Test
    void defaultExecutorCreatesANewThreadForEachAsyncInvocation() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AsyncPlaygroundConfig.class)) {
            AsyncTaskService service = context.getBean(AsyncTaskService.class);

            String firstThreadName = service.recordThreadName().get(2, TimeUnit.SECONDS);
            String secondThreadName = service.recordThreadName().get(2, TimeUnit.SECONDS);

            // TaskExecutor 빈을 등록하지 않았으니 AsyncExecutionInterceptor는
            // SimpleAsyncTaskExecutor로 폴백한다 - 호출마다 새 스레드를 만든다(28번 문서의
            // @Scheduled 기본값 - 스레드 하나를 공유 - 과 정반대다).
            assertThat(firstThreadName)
                    .isNotEqualTo(secondThreadName)
                    .isNotEqualTo(Thread.currentThread().getName());
        }
    }

    @Test
    void voidReturningMethodSwallowsTheExceptionAndRoutesItToTheUncaughtExceptionHandler() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AsyncPlaygroundConfig.class)) {
            AsyncTaskService service = context.getBean(AsyncTaskService.class);
            RecordingAsyncUncaughtExceptionHandler handler =
                    context.getBean(RecordingAsyncUncaughtExceptionHandler.class);

            // 예외를 던지는 메서드인데도 이 호출 자체는 예외 없이 즉시 반환된다 - void라서
            // 그 실패를 전달할 Future 자체가 없다.
            service.fireAndForgetThatThrows();

            boolean handled = handler.awaitHandled(2000);
            assertThat(handled).as("AsyncUncaughtExceptionHandler가 호출됐어야 한다").isTrue();
            assertThat(handler.lastMethodName()).isEqualTo("fireAndForgetThatThrows");
            assertThat(handler.lastException()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void futureReturningMethodPropagatesTheExceptionThroughGet() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AsyncPlaygroundConfig.class)) {
            AsyncTaskService service = context.getBean(AsyncTaskService.class);

            Future<String> future = service.returnsFutureThatThrows();

            // 반환 타입이 Future이면 AsyncExecutionAspectSupport#handleError()가 예외를
            // 삼키지 않고 그대로 다시 던진다 - get()을 부르는 쪽이 ExecutionException으로
            // 감싸인 원래 예외를 그대로 받는다.
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void selfInvocationRunsSynchronouslyOnTheCallingThread() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AsyncPlaygroundConfig.class)) {
            AsyncTaskService service = context.getBean(AsyncTaskService.class);
            String callerThreadName = Thread.currentThread().getName();

            String recordedThreadName = service.refreshViaSelfInvocation();

            // this.recordThreadName()이 프록시를 거치지 않으므로, 별도 스레드로 제출되지
            // 않고 호출자의 스레드에서 그대로 실행된다.
            assertThat(recordedThreadName).isEqualTo(callerThreadName);
        }
    }

    @Test
    void withoutEnableAsyncTheAnnotationIsInertAndTheMethodRunsSynchronously() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(UnenabledAsyncConfig.class)) {
            UnenabledAsyncService service = context.getBean(UnenabledAsyncService.class);
            String callerThreadName = Thread.currentThread().getName();

            // @EnableAsync가 없으므로 AsyncAnnotationBeanPostProcessor 자체가 등록되지
            // 않는다 - 프록시가 없으니 @Async는 그냥 읽히지 않는 메타데이터로 남고, 호출은
            // 평범한 동기 호출이 된다.
            String recordedThreadName = service.recordThreadName();

            assertThat(recordedThreadName).isEqualTo(callerThreadName);
        }
    }
}
