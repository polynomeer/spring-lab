package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;

/**
 * DispatcherServlet 요청 흐름(docs/plan/03-learning-dashboard-design.md 6.4절) 시나리오의
 * semantic 해석기다. 라이브 진입점은 {@code experiments:dispatcher-servlet-trace}의
 * {@code DispatcherServletTraceLab}(임베디드 Tomcat + 실제 DispatcherServlet) - 이 클래스의
 * 5개 이벤트 타입은 실제 jdi-tracer 세션으로 검증됐다(각 클래스/메서드 이름이 실제 히트와
 * 정확히 일치함을 확인했다).
 *
 * <p>{@code invokeAndHandle}은 어떤 컨트롤러 메서드인지가 인스턴스({@code this})에 담겨
 * 있는데, JDI의 {@code visibleVariables()}는 {@code this}를 포함하지 않는다 - 그래서
 * {@code CONTROLLER_INVOKED}는 어떤 메서드가 호출됐는지까지는 담지 못한다. 이건 지어내지
 * 않고 그대로 남겨 둔 한계다({@code StackFrame#thisObject()}로 메울 수는 있지만, 다른
 * 시나리오들도 이 한계를 안고 가는 것과 일관되게 지금은 손대지 않는다).
 */
public final class DispatcherFlowInterpreter implements ScenarioInterpreter {

    public static final String REQUEST_RECEIVED = "REQUEST_RECEIVED";
    public static final String HANDLER_LOOKUP_STARTED = "HANDLER_LOOKUP_STARTED";
    public static final String INTERCEPTOR_CHAIN_STARTED = "INTERCEPTOR_CHAIN_STARTED";
    public static final String CONTROLLER_INVOKED = "CONTROLLER_INVOKED";
    public static final String EXCEPTION_RESOLUTION_STARTED = "EXCEPTION_RESOLUTION_STARTED";

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        return switch (className + "#" + methodName) {
            case "DispatcherServlet#doDispatch" -> List.of(SemanticEvent.of(REQUEST_RECEIVED, hit.hitId()));
            case "DispatcherServlet#getHandler" -> List.of(SemanticEvent.of(HANDLER_LOOKUP_STARTED, hit.hitId()));
            case "HandlerExecutionChain#applyPreHandle" -> List.of(SemanticEvent.of(INTERCEPTOR_CHAIN_STARTED, hit.hitId()));
            case "ServletInvocableHandlerMethod#invokeAndHandle" -> List.of(SemanticEvent.of(CONTROLLER_INVOKED, hit.hitId()));
            case "HandlerExceptionResolverComposite#resolveException" -> List.of(SemanticEvent.of(EXCEPTION_RESOLUTION_STARTED, hit.hitId()));
            default -> List.of();
        };
    }

    private static String simpleName(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedClassName : fullyQualifiedClassName.substring(lastDot + 1);
    }
}
