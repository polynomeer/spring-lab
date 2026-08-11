package lab.sampleapp.orderplatform.event;

/**
 * Spring 4.2+는 ApplicationEvent를 상속하지 않은 평범한 POJO도 이벤트로 발행할 수 있게
 * 해 준다 - 21주차 애플리케이션 이벤트 문서에서 이미 확인한 것을 여기서 그대로 활용한다.
 */
public record OrderCompletedEvent(long orderId, String memberId, long amountWon) {
}
