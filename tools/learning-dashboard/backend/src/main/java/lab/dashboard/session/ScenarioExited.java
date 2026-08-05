package lab.dashboard.session;

/** 대상 프로세스가 끝까지 실행돼 종료됐다는 신호. */
public record ScenarioExited(String scenarioName, int totalHits) {
}
