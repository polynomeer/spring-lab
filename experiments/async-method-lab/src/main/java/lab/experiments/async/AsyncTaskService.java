package lab.experiments.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface AsyncTaskService {

    CompletableFuture<String> recordThreadName();

    void fireAndForgetThatThrows();

    Future<String> returnsFutureThatThrows();

    String refreshViaSelfInvocation();
}
