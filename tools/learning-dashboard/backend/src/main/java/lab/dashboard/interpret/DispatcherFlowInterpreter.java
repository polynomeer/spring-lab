package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;

/**
 * DispatcherServlet 요청 흐름(docs/plan/03-learning-dashboard-design.md 6.4절) 시나리오의
 * semantic 해석기다. 다른 세 해석기와 달리, 이 시나리오는 아직 라이브로 실행 가능한 Lab이
 * 없다(임베디드 서버 기동 + 요청 주입 인프라가 필요해 4단계로 미뤄 뒀다) - 그래서 이 클래스는
 * 실제 jdi-tracer 세션이 아니라, 이미 검증된 실제 Spring 소스 메서드 시그니처
 * ({@code DispatcherServlet#doDispatch}, {@code #getHandler},
 * {@code HandlerExecutionChain#applyPreHandle},
 * {@code ServletInvocableHandlerMethod#invokeAndHandle},
 * {@code HandlerExceptionResolverComposite#resolveException} - docs/15-dispatcher-servlet,
 * docs/16-controller-invocation, docs/22-mvc-exception-handling에서 이미 확인된 것들)을
 * 근거로 미리 작성됐고, 합성(synthetic) 히트로만 검증돼 있다.
 *
 * <p>{@code invokeAndHandle}은 어떤 컨트롤러 메서드인지가 인스턴스({@code this})에 담겨
 * 있는데, JDI의 {@code visibleVariables()}는 {@code this}를 포함하지 않는다 - 그래서
 * {@code CONTROLLER_INVOKED}는 어떤 메서드가 호출됐는지까지는 담지 못한다. 라이브 Lab을
 * 실제로 만들 때(4단계) 이 한계를 어떻게 메울지(예: {@code StackFrame#thisObject()}를
 * 별도로 캡처하도록 HitSerializer를 확장하는 것) 다시 판단해야 한다.
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
