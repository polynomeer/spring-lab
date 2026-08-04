package lab.dashboard.interpret;

import java.util.Map;

/**
 * 원본 {@link lab.tools.jdi.TraceEvent} 하나(또는 몇 개)를, 그 시나리오에서 의미 있는
 * 고수준 사실로 옮긴 것 - docs/plan/03-learning-dashboard-design.md 7번 절의 "semantic
 * 이벤트" 그대로다. {@code sourceHitId}로 원본 히트와 다시 연결할 수 있다.
 */
public record SemanticEvent(String type, int sourceHitId, Map<String, String> attributes) {

    public static SemanticEvent of(String type, int sourceHitId) {
        return new SemanticEvent(type, sourceHitId, Map.of());
    }

    public static SemanticEvent of(String type, int sourceHitId, String key, String value) {
        return new SemanticEvent(type, sourceHitId, Map.of(key, value));
    }

    public static SemanticEvent of(String type, int sourceHitId, String k1, String v1, String k2, String v2) {
        return new SemanticEvent(type, sourceHitId, Map.of(k1, v1, k2, v2));
    }
}
