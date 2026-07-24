package lab.experiments.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LifecycleEventLog {

    private static final List<String> events = new CopyOnWriteArrayList<>();

    private LifecycleEventLog() {
    }

    public static void record(String event) {
        events.add(event);
    }

    public static List<String> events() {
        return List.copyOf(events);
    }

    public static void reset() {
        events.clear();
    }
}
