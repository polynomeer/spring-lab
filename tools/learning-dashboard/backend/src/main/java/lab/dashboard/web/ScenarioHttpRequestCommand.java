package lab.dashboard.web;

/** {@code /app/scenario/http-request}로 들어오는 STOMP 메시지 본문 - dispatcher-flow의 "요청 보내기". */
public record ScenarioHttpRequestCommand(String method, String path, String body) {
}
