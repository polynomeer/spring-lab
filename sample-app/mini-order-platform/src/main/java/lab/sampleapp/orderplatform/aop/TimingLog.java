package lab.sampleapp.orderplatform.aop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component
public class TimingLog {

    public record Entry(String method, long elapsedNanos) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    void record(String method, long elapsedNanos) {
        entries.add(new Entry(method, elapsedNanos));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
