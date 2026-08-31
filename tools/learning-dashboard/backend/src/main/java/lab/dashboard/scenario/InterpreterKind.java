package lab.dashboard.scenario;

import java.util.List;
import java.util.function.Supplier;

import lab.dashboard.interpret.AutoProxyInterpreter;
import lab.dashboard.interpret.BeanLifecycleInterpreter;
import lab.dashboard.interpret.DispatcherFlowInterpreter;
import lab.dashboard.interpret.EventMulticastInterpreter;
import lab.dashboard.interpret.ExceptionResolutionInterpreter;
import lab.dashboard.interpret.ScenarioInterpreter;
import lab.dashboard.interpret.TransactionPropagationInterpreter;

/**
 * 시나리오가 손으로 짠 semantic 해석기를 쓸지, 원본 이벤트 로그만으로 만족할지를 고른다.
 * 기존 {@code ScenarioCatalog}에 하드코딩돼 있던 여섯 개 해석기는 여전히 평범한 자바
 * 클래스로 남아 있다 - DB로 옮겨간 건 "어느 시나리오가 어느 해석기를 쓰는가"라는 매핑뿐이다.
 * 사용자가 새로 등록하는 시나리오는 항상 {@link #NONE}이다 - 해석기는 그 시나리오가 충분히
 * 안정되고 반복해서 쓸 가치가 있을 때 나중에 손으로 추가하는 선택지로 남겨 둔다
 * (docs/plan/04-dynamic-scenario-design.md 1번 절).
 */
public enum InterpreterKind {

    NONE(() -> hit -> List.of()),
    BEAN_LIFECYCLE(BeanLifecycleInterpreter::new),
    AUTO_PROXY(AutoProxyInterpreter::new),
    TX_PROPAGATION(TransactionPropagationInterpreter::new),
    DISPATCHER_FLOW(DispatcherFlowInterpreter::new),
    EVENT_MULTICAST(EventMulticastInterpreter::new),
    EXCEPTION_RESOLUTION(ExceptionResolutionInterpreter::new);

    private final Supplier<ScenarioInterpreter> factory;

    InterpreterKind(Supplier<ScenarioInterpreter> factory) {
        this.factory = factory;
    }

    public Supplier<ScenarioInterpreter> factory() {
        return factory;
    }
}
