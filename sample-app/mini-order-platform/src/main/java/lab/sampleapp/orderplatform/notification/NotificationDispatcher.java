package lab.sampleapp.orderplatform.notification;

import java.util.List;

import lab.sampleapp.orderplatform.domain.Member;

/**
 * List&lt;NotificationChannel&gt;을 그대로 주입받아 등록된 모든 채널에 브로드캐스트한다.
 * PaymentGatewayRegistry(List -&gt; Map, 키로 하나만 골라 쓰기)와 대비되는 "전체를 순서대로
 * 쓰는" 컬렉션 주입 사용 패턴 - Spring이 List&lt;T&gt;를 채울 때 @Order를 존중해 정렬해
 * 준다는 점도 여기서 같이 확인한다.
 *
 * <p>Phase 6에서 {@code @Component}를 떼고
 * {@code lab.sampleapp.orderplatform.boot.NotificationAutoConfiguration}으로 등록을 옮겼다.
 */
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    public void broadcast(Member to, String message) {
        for (NotificationChannel channel : channels) {
            channel.send(to, message);
        }
    }

    public List<String> channelTypesInOrder() {
        return channels.stream().map(NotificationChannel::channelType).toList();
    }
}
