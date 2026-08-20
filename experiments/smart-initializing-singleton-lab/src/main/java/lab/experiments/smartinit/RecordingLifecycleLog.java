package lab.experiments.smartinit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingLifecycleLog {

    private final List<String> events = new CopyOnWriteArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
