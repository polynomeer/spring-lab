package lab.dashboard.conditionreport;

import java.util.Map;

/**
 * {@link ConditionReportRunner#run}이 한 번 실행을 끝낸 결과 - {@code reportJson}/{@code error}는
 * 서로 배타적이다({@link lab.dashboard.session.ScenarioHttpResponseReceived}와 같은 패턴).
 * {@code reportJson}은 {@code ConditionReportLab}이 손으로 만든 JSON 문자열 그대로다 -
 * 이 이벤트 자신은 그 내용을 해석하지 않는다.
 */
public record ConditionReportReceived(Map<String, String> propertyOverrides, String reportJson, String error) {
}
