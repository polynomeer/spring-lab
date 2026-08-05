package lab.dashboard.session;

import lab.dashboard.interpret.SemanticEvent;

/** 시나리오별 해석기가 원본 히트로부터 파생시킨 의미 있는 이벤트. */
public record SemanticEventReceived(String scenarioName, SemanticEvent semanticEvent) {
}
