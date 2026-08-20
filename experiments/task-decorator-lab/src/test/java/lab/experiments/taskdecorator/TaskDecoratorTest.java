package lab.experiments.taskdecorator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDecoratorTest {

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void withoutADecoratorTheAsyncThreadNeverSeesTheCallersRequestContext() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WithoutDecoratorConfig.class)) {
            AsyncContextService service = context.getBean(AsyncContextService.class);

            RequestContext.set("caller-context");
            CompletableFuture<String> future = service.readRequestContext();

            // ThreadLocal은 절대 스스로 스레드 경계를 넘지 않는다 - 풀 스레드는 이 값에 대해
            // 아무것도 모른다.
            assertThat(future.get(2, TimeUnit.SECONDS)).isNull();
        }
    }

    @Test
    void withADecoratorTheAsyncThreadSeesTheCallersRequestContext() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WithDecoratorConfig.class)) {
            AsyncContextService service = context.getBean(AsyncContextService.class);

            RequestContext.set("caller-context");
            CompletableFuture<String> future = service.readRequestContext();

            assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("caller-context");
        }
    }

    @Test
    void eachSubmissionCapturesItsOwnCallersContextRatherThanAStaleValue() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WithDecoratorConfig.class)) {
            AsyncContextService service = context.getBean(AsyncContextService.class);

            // decorate()는 매 제출마다 그 순간의 호출 스레드 상태를 새로 캡처한다 -
            // TaskDecorator 인스턴스 자체는 설정 시점에 딱 한 번만 만들어졌는데도 그렇다.
            RequestContext.set("first");
            assertThat(service.readRequestContext().get(2, TimeUnit.SECONDS)).isEqualTo("first");

            RequestContext.set("second");
            assertThat(service.readRequestContext().get(2, TimeUnit.SECONDS)).isEqualTo("second");
        }
    }

    @Test
    void theDecoratorRestoresThePoolThreadsStateSoAnUnrelatedLaterTaskDoesNotInheritIt() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(WithDecoratorConfig.class)) {
            AsyncContextService service = context.getBean(AsyncContextService.class);

            RequestContext.set("task-a-context");
            assertThat(service.readRequestContext().get(2, TimeUnit.SECONDS)).isEqualTo("task-a-context");

            // corePoolSize=maxPoolSize=1이라 정확히 같은 풀 스레드가 재사용된다 - 이번엔
            // 호출 스레드에 아무 컨텍스트도 없다. finally에서 이전 상태를 복원(또는 정리)해
            // 두지 않았다면, 방금 그 스레드에 남아 있던 "task-a-context"가 이 무관한 작업으로
            // 새어 나왔을 것이다.
            RequestContext.clear();
            String secondResult = service.readRequestContext().get(2, TimeUnit.SECONDS);

            assertThat(secondResult).isNull();
        }
    }
}
