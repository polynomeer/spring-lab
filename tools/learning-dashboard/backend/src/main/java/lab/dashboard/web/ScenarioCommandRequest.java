package lab.dashboard.web;

/** {@code /app/scenario/command}로 들어오는 STOMP 메시지 본문. */
public record ScenarioCommandRequest(String cmd, Long intervalMs) {
}
