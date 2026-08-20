package lab.experiments.smartinit;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

public class ContextRefreshedListener {

    private final RecordingLifecycleLog log;

    public ContextRefreshedListener(RecordingLifecycleLog log) {
        this.log = log;
    }

    @EventListener
    public void onRefreshed(ContextRefreshedEvent event) {
        log.record("ContextRefreshedEvent");
    }
}
