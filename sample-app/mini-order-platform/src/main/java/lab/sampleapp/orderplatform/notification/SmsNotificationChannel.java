package lab.sampleapp.orderplatform.notification;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lab.sampleapp.orderplatform.domain.Member;

@Component
@Order(2)
public class SmsNotificationChannel implements NotificationChannel {

    private final SentNotificationLog log;

    public SmsNotificationChannel(SentNotificationLog log) {
        this.log = log;
    }

    @Override
    public String channelType() {
        return "sms";
    }

    @Override
    public void send(Member to, String message) {
        log.record(channelType(), to.id(), message);
    }
}
