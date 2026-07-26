package lab.experiments.depres;

public class GreeterConsumer {

    private final Greeter greeter;

    public GreeterConsumer(Greeter greeter) {
        this.greeter = greeter;
    }

    public Greeter getGreeter() {
        return greeter;
    }
}
