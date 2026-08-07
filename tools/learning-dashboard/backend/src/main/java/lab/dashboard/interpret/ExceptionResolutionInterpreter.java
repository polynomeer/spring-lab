package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;

/**
 * MVC 예외 처리 우선순위(docs/22-mvc-exception-handling.md) 시나리오의 semantic 해석기.
 * 라이브 진입점은 {@code experiments:mvc-exception-pipeline}의 {@code ExceptionPipelineLab}
 * (dispatcher-flow와 같은 임베디드 Tomcat 패턴) - 5개 이벤트 타입 모두 실제 jdi-tracer
 * 세션으로 검증했다.
 *
 * <p>{@code HandlerExceptionResolverComposite}가 등록된 리졸버를 순서대로 시도하다가 하나가
 * {@code ModelAndView}를 반환하면 멈춘다 - 그래서 어느 리졸버가 최종적으로 처리했는지는, 그
 * 리졸버 이후의 리졸버들 히트가 아예 없다는 사실(트레이스가 거기서 멈춘다)로 알 수 있다.
 * dispatcher-flow의 파이프라인과 정확히 같은 원리다.
 *
 * <p>{@code ResponseStatusExceptionResolver#doResolveException}은 예외 자신에게
 * {@code @ResponseStatus}가 없으면 {@code ex.getCause()}로 재귀 호출한다(실제 jdi-tracer
 * 세션으로 확인 - docs/22 7번 절 참고) - 원인 체인이 있는 예외 하나에 이 브레이크포인트가
 * 여러 번 걸릴 수 있다는 뜻이다. 이 해석기는 그 반복 히트를 억지로 하나로 합치지 않는다 -
 * 매 히트가 실제로 일어난 별개의 시도이기 때문이다(publishEvent 오버로드 캐스케이드처럼
 * 같은 발행 1번이 중복 계산되는 경우와는 다르다).
 */
public final class ExceptionResolutionInterpreter implements ScenarioInterpreter {

    public static final String EXCEPTION_HANDLING_STARTED = "EXCEPTION_HANDLING_STARTED";
    public static final String RESOLVER_CHAIN_ENTERED = "RESOLVER_CHAIN_ENTERED";
    public static final String EXCEPTION_HANDLER_LOOKUP_STARTED = "EXCEPTION_HANDLER_LOOKUP_STARTED";
    public static final String RESPONSE_STATUS_RESOLUTION_STARTED = "RESPONSE_STATUS_RESOLUTION_STARTED";
    public static final String DEFAULT_RESOLUTION_STARTED = "DEFAULT_RESOLUTION_STARTED";

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        return switch (className + "#" + methodName) {
            case "DispatcherServlet#processHandlerException" -> List.of(SemanticEvent.of(EXCEPTION_HANDLING_STARTED, hit.hitId()));
            case "HandlerExceptionResolverComposite#resolveException" -> List.of(SemanticEvent.of(RESOLVER_CHAIN_ENTERED, hit.hitId()));
            case "ExceptionHandlerExceptionResolver#getExceptionHandlerMethod" ->
                    List.of(SemanticEvent.of(EXCEPTION_HANDLER_LOOKUP_STARTED, hit.hitId()));
            case "ResponseStatusExceptionResolver#doResolveException" ->
                    List.of(SemanticEvent.of(RESPONSE_STATUS_RESOLUTION_STARTED, hit.hitId()));
            case "DefaultHandlerExceptionResolver#doResolveException" -> List.of(SemanticEvent.of(DEFAULT_RESOLUTION_STARTED, hit.hitId()));
            default -> List.of();
        };
    }

    private static String simpleName(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedClassName : fullyQualifiedClassName.substring(lastDot + 1);
    }
}
