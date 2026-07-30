package lab.experiments.autoconfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLog {

    private final List<String> entries = new CopyOnWriteArrayList<>();

    public void record(String entry) {
        entries.add(entry);
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }
}
