package lab.experiments.configphase;

public class SharedValue {

    private final String source;

    public SharedValue(String source) {
        this.source = source;
    }

    public String source() {
        return source;
    }
}
