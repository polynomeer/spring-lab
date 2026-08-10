package lab.sampleapp.plugindiscovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

// 실제 이메일/Slack/SMS 발송 대신, 각 플러그인이 "무엇을 보냈다고 주장했는지"를 관찰하기
// 위한 기록기다 - EventLog(application-event-lab)와 같은 역할.
@Component
public class SentMessageLog {

    public record Entry(String pluginType, NotificationMessage message) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String pluginType, NotificationMessage message) {
        entries.add(new Entry(pluginType, message));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
