package lab.experiments.factorybean;

import java.util.concurrent.atomic.AtomicInteger;

public class Widget {

    private static final AtomicInteger sequence = new AtomicInteger();

    private final int id = sequence.incrementAndGet();

    public int id() {
        return id;
    }
}
