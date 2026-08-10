package lab.sampleapp.plugindiscovery;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 우선순위(@Order)상 가장 먼저 시도돼야 할 채널이지만, 이 환경엔 Slack webhook이 설정돼
// 있지 않다고 가정해 enabled()를 false로 뒀다 - "발견됨"과 "지금 쓸 수 있음"이 다르다는 걸
// registry.firstEnabled()가 이 플러그인을 건너뛰는 것으로 직접 확인한다.
@Component
@Order(1)
public class SlackNotificationPlugin implements NotificationPlugin {

    private final SentMessageLog log;

    public SlackNotificationPlugin(SentMessageLog log) {
        this.log = log;
    }

    @Override
    public String type() {
        return "slack";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void send(NotificationMessage message) {
        log.record(type(), message);
    }
}
