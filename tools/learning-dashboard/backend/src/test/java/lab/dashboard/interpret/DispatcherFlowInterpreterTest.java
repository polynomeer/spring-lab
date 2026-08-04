package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 아직 라이브 Lab이 없는 시나리오(DispatcherFlowInterpreter 클래스 Javadoc 참고)라, 합성
// 히트로 검증한다 - 각 메서드 시그니처 자체는 실제 spring-framework v6.2.19 소스로
// 확인됐다(docs/15-dispatcher-servlet, docs/22-mvc-exception-handling).
class DispatcherFlowInterpreterTest {

    @Test
    void translatesTheDocumentedRequestFlowMethodsIntoSemanticEvents() {
        DispatcherFlowInterpreter interpreter = new DispatcherFlowInterpreter();

        List<TraceEvent> hits = List.of(
                hit(1, "org.springframework.web.servlet.DispatcherServlet", "doDispatch"),
                hit(2, "org.springframework.web.servlet.DispatcherServlet", "getHandler"),
                hit(3, "org.springframework.web.servlet.HandlerExecutionChain", "applyPreHandle"),
                hit(4, "org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod", "invokeAndHandle"));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(
                DispatcherFlowInterpreter.REQUEST_RECEIVED,
                DispatcherFlowInterpreter.HANDLER_LOOKUP_STARTED,
                DispatcherFlowInterpreter.INTERCEPTOR_CHAIN_STARTED,
                DispatcherFlowInterpreter.CONTROLLER_INVOKED);
    }

    @Test
    void aResolvedExceptionProducesAnExceptionResolutionStartedEvent() {
        DispatcherFlowInterpreter interpreter = new DispatcherFlowInterpreter();

        List<SemanticEvent> events = interpreter.onHit(hit(5,
                "org.springframework.web.servlet.handler.HandlerExceptionResolverComposite", "resolveException"));

        assertThat(events).extracting(SemanticEvent::type)
                .containsExactly(DispatcherFlowInterpreter.EXCEPTION_RESOLUTION_STARTED);
    }

    @Test
    void ignoresHitsOutsideThisScenariosVocabulary() {
        DispatcherFlowInterpreter interpreter = new DispatcherFlowInterpreter();

        List<SemanticEvent> events = interpreter.onHit(
                hit(6, "org.springframework.web.servlet.FrameworkServlet", "processRequest"));

        assertThat(events).isEmpty();
    }

    private static TraceEvent hit(int hitId, String className, String methodName) {
        return new TraceEvent(
                hitId, hitId, "main",
                new TraceEvent.Location(className, methodName, 1),
                List.of(new TraceEvent.Frame(className, methodName)),
                List.of(),
                true);
    }
}
