package lab.dashboard.session;

/**
 * {@link ScenarioSession#sendHttpRequest}가 쏜 요청의 결과 - {@code status}와 {@code error}는
 * 배타적이다(성공하면 status만, 예외가 나면 error만 채워진다).
 */
public record ScenarioHttpResponseReceived(String scenarioName, String method, String path, Integer status, String error) {
}
