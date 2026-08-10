package lab.sampleapp.orderplatform.notification;

import lab.sampleapp.orderplatform.domain.Member;
import lab.sampleapp.orderplatform.plugin.SelfDescribingPlugin;

public interface NotificationChannel extends SelfDescribingPlugin {

    String channelType();

    void send(Member to, String message);

    @Override
    default String pluginKind() {
        return "notification-channel";
    }

    @Override
    default String pluginKey() {
        return channelType();
    }
}
