package lab.experiments.composedannotation;

public class EngineConsumer {

    private final Engine engine;

    public EngineConsumer(Engine engine) {
        this.engine = engine;
    }

    public Engine engine() {
        return engine;
    }
}
