package lab.experiments.taskdecorator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// WithDecoratorConfig와 완전히 같지만 setTaskDecorator() 호출이 없다 - 대조군.
@Configuration
@EnableAsync
public class WithoutDecoratorConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("without-decorator-");
        return executor;
    }

    @Bean
    public AsyncContextService asyncContextService() {
        return new AsyncContextService();
    }
}
