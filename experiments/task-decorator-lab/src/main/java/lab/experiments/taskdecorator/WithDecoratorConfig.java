package lab.experiments.taskdecorator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class WithDecoratorConfig {

    // 스레드 하나짜리 풀 - 스레드 재사용을 결정론적으로 만들어서, 이전 작업이 남긴 상태가
    // 새어 나가는지(누출)를 확실하게 재현하기 위함.
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("with-decorator-");
        executor.setTaskDecorator(new RequestContextPropagatingTaskDecorator());
        return executor;
    }

    @Bean
    public AsyncContextService asyncContextService() {
        return new AsyncContextService();
    }
}
