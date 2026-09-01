package lab.dashboard.web;

/**
 * {@code /app/scenario/comparison/start}로 들어오는 STOMP 메시지 본문 -
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행".
 */
public record ComparisonStartRequest(String nameA, String nameB, long intervalMs) {
}
