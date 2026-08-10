package lab.sampleapp.plugindiscovery;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class EmailNotificationPlugin implements NotificationPlugin {

    private final SentMessageLog log;

    public EmailNotificationPlugin(SentMessageLog log) {
        this.log = log;
    }

    @Override
    public String type() {
        return "email";
    }

    @Override
    public void send(NotificationMessage message) {
        log.record(type(), message);
    }
}
