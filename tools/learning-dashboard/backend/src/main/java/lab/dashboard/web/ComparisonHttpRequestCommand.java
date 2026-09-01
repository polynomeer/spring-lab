package lab.dashboard.web;

/**
 * {@code /app/scenario/comparison/http-request}로 들어오는 STOMP 메시지 본문 - A/B 비교
 * 화면의 "요청 보내기". 대상이 A/B 둘 중 하나로 모호할 수 있으므로 {@code name}으로
 * 정확히 지정한다({@link ScenarioHttpRequestCommand}와 달리).
 */
public record ComparisonHttpRequestCommand(String name, String method, String path, String body) {
}
