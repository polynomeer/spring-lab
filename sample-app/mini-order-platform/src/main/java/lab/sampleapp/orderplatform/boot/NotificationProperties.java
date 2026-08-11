package lab.sampleapp.orderplatform.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("order-platform.notification")
public class NotificationProperties {

    /** false면 NotificationAutoConfiguration이 NotificationDispatcher를 아예 등록하지 않는다. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
