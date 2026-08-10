package lab.sampleapp.orderplatform.aop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component
public class AuditLog {

    public record Entry(String actor, String action, boolean success, String detail) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    void record(String actor, String action, boolean success, String detail) {
        entries.add(new Entry(actor, action, success, detail));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
