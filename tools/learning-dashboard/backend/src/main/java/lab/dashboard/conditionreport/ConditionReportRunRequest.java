package lab.dashboard.conditionreport;

import java.util.Map;

/** {@code /app/condition-report/run}으로 들어오는 STOMP 메시지 본문 - 프로퍼티 오버라이드 프리셋. */
public record ConditionReportRunRequest(Map<String, String> overrides) {
}
