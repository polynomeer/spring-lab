package lab.sampleapp.plugindiscovery;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class SmsNotificationPlugin implements NotificationPlugin {

    private final SentMessageLog log;

    public SmsNotificationPlugin(SentMessageLog log) {
        this.log = log;
    }

    @Override
    public String type() {
        return "sms";
    }

    @Override
    public void send(NotificationMessage message) {
        log.record(type(), message);
    }
}
