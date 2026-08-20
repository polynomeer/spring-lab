package lab.experiments.exposeproxy;

import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class LoggingAspect {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Before("@annotation(LoggedOperation)")
    public void logBefore() {
        invocationCount.incrementAndGet();
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
