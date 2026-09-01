package lab.dashboard.session;

/**
 * 새 시나리오 실행(자식 JVM launch)이 막 시작됐다는 신호 - {@code ScenarioRunRecorder}
 * (실행 히스토리 기록)가 이걸 받아야 "이전 실행의 녹화 버퍼를 정리하고 새로 시작한다"는
 * 걸 알 수 있다. 웹소켓 계층은 이 이벤트를 브라우저로 중계하지 않는다 - 프론트엔드는
 * 이미 STOMP start 메시지를 자신이 보냈다는 걸 알고 있어서 별도 확인이 필요 없다.
 */
public record ScenarioStarted(String scenarioName) {
}
