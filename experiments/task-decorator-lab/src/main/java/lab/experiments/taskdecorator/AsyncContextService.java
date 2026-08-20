package lab.experiments.taskdecorator;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

public class AsyncContextService {

    @Async
    public CompletableFuture<String> readRequestContext() {
        return CompletableFuture.completedFuture(RequestContext.get());
    }
}
