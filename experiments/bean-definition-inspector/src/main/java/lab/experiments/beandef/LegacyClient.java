package lab.experiments.beandef;

public class LegacyClient {

    private LegacyClient() {
    }

    public static LegacyClient create() {
        return new LegacyClient();
    }
}
