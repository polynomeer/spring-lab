package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 jdi-tracer 세션(experiments/mvc-exception-pipeline의 ExceptionPipelineLab)에서 관찰한
// 히트 시퀀스를 옮겨 검증한다 - docs/22-mvc-exception-handling.md 7번 절 참고.
class ExceptionResolutionInterpreterTest {

    @Test
    void aControllerLocalHandlerStopsTheChainAtTheExceptionHandlerResolver() {
        ExceptionResolutionInterpreter interpreter = new ExceptionResolutionInterpreter();

        // GET /widgets/boom-local - 컨트롤러 로컬 @ExceptionHandler가 즉시 처리(418), 뒤의
        // 두 리졸버는 히트조차 없다.
        List<TraceEvent> hits = List.of(
                hit(1, "org.springframework.web.servlet.DispatcherServlet", "processHandlerException"),
                hit(2, "org.springframework.web.servlet.handler.HandlerExceptionResolverComposite", "resolveException"),
                hit(3, "org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver", "getExceptionHandlerMethod"));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(
                ExceptionResolutionInterpreter.EXCEPTION_HANDLING_STARTED,
                ExceptionResolutionInterpreter.RESOLVER_CHAIN_ENTERED,
                ExceptionResolutionInterpreter.EXCEPTION_HANDLER_LOOKUP_STARTED);
    }

    @Test
    void responseStatusExceptionResolverRecursesOnTheCauseAndEachHitIsKeptSeparate() {
        ExceptionResolutionInterpreter interpreter = new ExceptionResolutionInterpreter();

        // GET /widgets/abc - MethodArgumentTypeMismatchException의 원인(NumberFormatException)으로
        // doResolveException이 재귀 호출되어 이 브레이크포인트가 두 번 걸린다(실제 관찰됨) - 억지로
        // 하나로 합치지 않고 둘 다 그대로 이벤트가 된다.
        List<TraceEvent> hits = List.of(
                hit(1, "org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver", "doResolveException"),
                hit(2, "org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver", "doResolveException"),
                hit(3, "org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver", "doResolveException"));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(
                ExceptionResolutionInterpreter.RESPONSE_STATUS_RESOLUTION_STARTED,
                ExceptionResolutionInterpreter.RESPONSE_STATUS_RESOLUTION_STARTED,
                ExceptionResolutionInterpreter.DEFAULT_RESOLUTION_STARTED);
    }

    @Test
    void ignoresHitsOutsideThisScenariosVocabulary() {
        ExceptionResolutionInterpreter interpreter = new ExceptionResolutionInterpreter();

        List<SemanticEvent> events = interpreter.onHit(
                hit(9, "org.springframework.web.servlet.DispatcherServlet", "doDispatch"));

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
