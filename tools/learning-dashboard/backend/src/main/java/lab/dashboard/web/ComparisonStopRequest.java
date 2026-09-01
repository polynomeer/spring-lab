package lab.dashboard.web;

/** {@code /app/scenario/comparison/stop}로 들어오는 STOMP 메시지 본문 - 이름 하나만 정리한다. */
public record ComparisonStopRequest(String name) {
}
