package lab.sampleapp.orderplatform.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lab.sampleapp.orderplatform.domain.Member;
import lab.sampleapp.orderplatform.domain.MembershipTier;
import lab.sampleapp.orderplatform.notification.NotificationDispatcher;

/**
 * AFTER_COMMIT이라 OrderPlacementService.placeOrder()가 실제로 커밋된 뒤에만 실행된다 -
 * 커밋되지 않은(롤백된) 주문에 대해 "주문이 완료됐습니다" 알림을 보내는 사고를 구조적으로
 * 막는다.
 *
 * <p>이 모듈에는 아직 실제 회원 조회 저장소가 없어서(1번 절 참고), 알림을 보낼 때
 * memberId만으로 최소한의 Member를 즉석에서 만든다 - 실제 조회가 필요해지면 이 부분만
 * MemberRepository로 교체하면 된다.
 */
@Component
public class NotificationDispatchListener {

    private final NotificationDispatcher notificationDispatcher;

    public NotificationDispatchListener(NotificationDispatcher notificationDispatcher) {
        this.notificationDispatcher = notificationDispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        Member member = new Member(event.memberId(), event.memberId(), MembershipTier.BASIC);
        notificationDispatcher.broadcast(member, "order " + event.orderId() + " completed");
    }
}
