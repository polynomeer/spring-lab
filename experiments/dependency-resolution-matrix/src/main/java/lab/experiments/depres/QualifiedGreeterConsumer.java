package lab.experiments.depres;

import org.springframework.beans.factory.annotation.Qualifier;

public class QualifiedGreeterConsumer {

    private final Greeter greeter;

    public QualifiedGreeterConsumer(@Qualifier("secondaryGreeter") Greeter greeter) {
        this.greeter = greeter;
    }

    public Greeter getGreeter() {
        return greeter;
    }
}
