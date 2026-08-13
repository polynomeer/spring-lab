package lab.experiments.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncPlaygroundConfig implements AsyncConfigurer {

    @Bean
    public RecordingAsyncUncaughtExceptionHandler recordingAsyncUncaughtExceptionHandler() {
        return new RecordingAsyncUncaughtExceptionHandler();
    }

    @Bean
    public AsyncTaskService asyncTaskService() {
        return new AsyncTaskServiceImpl();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return recordingAsyncUncaughtExceptionHandler();
    }

    // getAsyncExecutor()는 오버라이드하지 않는다 - AsyncConfigurer가 null을 반환하면
    // AsyncExecutionInterceptor 자신의 기본 탐색(TaskExecutor 빈 조회 → 없으면
    // SimpleAsyncTaskExecutor로 폴백)이 그대로 적용된다는 것을 확인하기 위해서다.
}
