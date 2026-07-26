package lab.ext.rewriter;

import org.springframework.stereotype.Component;

@DefaultChannel("email")
@Component
public class NotificationSender {

    private String channel;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
