package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;

/**
 * 하나의 시나리오(빈 생명주기, AOP 프록시 생성, 트랜잭션 전파, ...)에 대해, jdi-tracer가
 * 실시간으로 보내는 {@link TraceEvent}를 하나씩 받아 그 시나리오에 맞는
 * {@link SemanticEvent}로 옮기는 상태 있는 변환기다. 상태가 필요한 이유 - 예를 들어
 * 트랜잭션 전파에서 "이 resume이 어느 suspend와 짝인가"는 이전 히트들을 기억해야만 답할
 * 수 있다.
 */
public interface ScenarioInterpreter {

    /** 히트 하나를 처리하고, 그로부터 파생된 semantic 이벤트 0개 이상을 반환한다. */
    List<SemanticEvent> onHit(TraceEvent hit);
}
