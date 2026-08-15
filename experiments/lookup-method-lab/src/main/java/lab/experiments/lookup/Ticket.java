package lab.experiments.lookup;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Ticket {

    private static final AtomicInteger sequence = new AtomicInteger();

    private final int id = sequence.incrementAndGet();

    public int id() {
        return id;
    }
}
