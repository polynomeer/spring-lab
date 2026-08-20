package lab.experiments.smartinit;

import jakarta.annotation.PostConstruct;

public class BeanB {

    private final RecordingLifecycleLog log;
    private volatile boolean postConstructDone = false;

    public BeanB(RecordingLifecycleLog log) {
        this.log = log;
    }

    @PostConstruct
    void init() {
        postConstructDone = true;
        log.record("BeanB.postConstruct");
    }

    public boolean isPostConstructDone() {
        return postConstructDone;
    }
}
