package lab.sampleapp.orderplatform.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component
public class SentNotificationLog {

    public record Entry(String channelType, String memberId, String message) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String channelType, String memberId, String message) {
        entries.add(new Entry(channelType, memberId, message));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
