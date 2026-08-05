package lab.dashboard.session;

/** 대상 자식 JVM 자신이 System.out/err에 찍은 한 줄(TracerServer가 그대로 중계한 것). */
public record ScenarioStdoutReceived(String scenarioName, String stream, String line) {
}
