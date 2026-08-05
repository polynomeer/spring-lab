package lab.dashboard.session;

import lab.tools.jdi.TraceEvent;

/** TracerServer가 보낸 원본 히트 - Spring 이벤트로 발행되어 웹소켓 계층까지 전달된다. */
public record RawHitReceived(String scenarioName, TraceEvent traceEvent) {
}
