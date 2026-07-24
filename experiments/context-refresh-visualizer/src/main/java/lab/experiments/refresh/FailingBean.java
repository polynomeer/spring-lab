package lab.experiments.refresh;

public class FailingBean {

    public FailingBean() {
        throw new IllegalStateException("boom");
    }
}
