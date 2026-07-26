package lab.ext.rewriter;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@ForceLazy
@Component
public class LazyCandidate {

    public static final AtomicInteger constructorCalls = new AtomicInteger();

    public LazyCandidate() {
        constructorCalls.incrementAndGet();
    }
}
