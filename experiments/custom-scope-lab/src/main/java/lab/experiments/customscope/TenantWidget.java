package lab.experiments.customscope;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;

public class TenantWidget implements DisposableBean {

    private static final AtomicInteger sequence = new AtomicInteger();

    private final int id = sequence.incrementAndGet();
    private final AtomicBoolean destroyed = new AtomicBoolean();

    public int id() {
        return id;
    }

    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public void destroy() {
        destroyed.set(true);
    }
}
