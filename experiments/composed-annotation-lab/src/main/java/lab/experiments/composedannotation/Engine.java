package lab.experiments.composedannotation;

public class Engine {

    private final String label;

    public Engine(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
