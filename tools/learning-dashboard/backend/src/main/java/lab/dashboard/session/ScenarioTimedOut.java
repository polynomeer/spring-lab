package lab.dashboard.session;

/**
 * 즉석 코드 작성 시나리오(docs/plan/04-dynamic-scenario-design.md 2단계)가 실행 타임아웃을
 * 넘겨 강제 종료됐다는 신호 - 무한루프 같은 실수를 막는 안전장치가 실제로 작동했다는 뜻이다.
 */
public record ScenarioTimedOut(String scenarioName, long timeoutSeconds) {
}
