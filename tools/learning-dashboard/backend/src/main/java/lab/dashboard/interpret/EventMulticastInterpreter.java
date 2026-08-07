package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;

/**
 * 애플리케이션 이벤트 멀티캐스트(docs/21-application-events.md) 시나리오의 semantic
 * 해석기. 라이브 진입점은 {@code experiments:application-event-lab}의
 * {@code ApplicationEventLab} - 6개 이벤트 타입 모두 실제 jdi-tracer 세션으로 검증했다
 * (그 과정에서 문서 7번 절의 오류 하나도 함께 발견해 고쳤다 -
 * {@code TransactionalApplicationListenerMethodAdapter}가 아니라
 * {@code TransactionalApplicationListenerSynchronization#processEventWithCallbacks}가
 * 실제 커밋 후 콜백 지점이다).
 *
 * <p>진입 브레이크포인트만으로는 "어떤 리스너 메서드가 호출됐는지"를 알 수 없다 -
 * {@code invokeListener(ApplicationListener listener, ApplicationEvent event)}의 {@code listener}
 * 지역 변수는 JDI가 원격 {@code toString()}을 호출하지 않으므로 {@code "instance of
 * ApplicationListenerMethodAdapter(id=...)"} 형태로만 보인다 - 어떤 {@code @EventListener}
 * 메서드인지는 지어내지 않는다.
 *
 * <p><b>{@code invokeListener()}의 스레드는 동기/비동기를 구분해 주지 않는다</b> - 처음엔
 * {@code @Async} 리스너가 {@code multicastEvent()}의 {@code executor.execute(() ->
 * invokeListener(...))} 경로를 타서 스레드 이름이 다를 거라 예상했지만, 실제 jdi-tracer
 * 세션으로 확인해 보니 이 랩의 {@code @Async}는 {@code SimpleApplicationEventMulticaster}
 * 자체의 taskExecutor가 아니라 {@code @EnableAsync}의 AOP 프록시(
 * {@code AsyncExecutionInterceptor})가 대상 빈을 감싸는 방식이라, {@code invokeListener()}는
 * 어떤 리스너든 항상 발행자 스레드(main)에서 호출된다 - 실제 스레드 전환은 그 안쪽,
 * {@code doInvokeListener() → listener.onApplicationEvent() → 리플렉션 호출}이 프록시를
 * 통과하는 지점에서 일어나 이 브레이크포인트로는 보이지 않는다. 그래서 스레드 전환을 정직하게
 * 관찰하려면 대상 리스너 메서드 자체({@code OrderEventListeners#asyncListener})에
 * 브레이크포인트를 걸어야 했다 - 다른 프레임워크 내부 전용 시나리오들과 달리, 이 시나리오만
 * 대상 애플리케이션 코드에도 브레이크포인트가 하나 있는 이유다.
 *
 * <p>{@code publishEvent}는 오버로드가 3개라({@code ApplicationEvent}/{@code Object}/
 * {@code Object, ResolvableType}) 실제 발행 1번에 브레이크포인트가 2번 걸린다({@code
 * publishEvent(Object)}가 내부적으로 2-인자 delegate를 그대로 호출한다) - {@code typeHint}
 * 지역 변수가 있는 히트(2-인자 delegate)에서만 이벤트를 발행해 중복을 제거한다.
 */
public final class EventMulticastInterpreter implements ScenarioInterpreter {

    public static final String EVENT_PUBLISHED = "EVENT_PUBLISHED";
    public static final String MULTICAST_STARTED = "MULTICAST_STARTED";
    public static final String LISTENER_INVOKED = "LISTENER_INVOKED";
    public static final String ASYNC_LISTENER_EXECUTING = "ASYNC_LISTENER_EXECUTING";
    public static final String TX_LISTENER_EVENT_RECEIVED = "TX_LISTENER_EVENT_RECEIVED";
    public static final String TX_LISTENER_INVOKED = "TX_LISTENER_INVOKED";

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        return switch (className + "#" + methodName) {
            case "AbstractApplicationContext#publishEvent" -> hasLocal(hit, "typeHint")
                    ? List.of(SemanticEvent.of(EVENT_PUBLISHED, hit.hitId()))
                    : List.of();
            case "SimpleApplicationEventMulticaster#multicastEvent" -> List.of(SemanticEvent.of(MULTICAST_STARTED, hit.hitId()));
            case "SimpleApplicationEventMulticaster#invokeListener" ->
                    List.of(SemanticEvent.of(LISTENER_INVOKED, hit.hitId(), "thread", hit.thread()));
            case "OrderEventListeners#asyncListener" ->
                    List.of(SemanticEvent.of(ASYNC_LISTENER_EXECUTING, hit.hitId(), "thread", hit.thread()));
            case "TransactionalApplicationListenerMethodAdapter#onApplicationEvent" ->
                    List.of(SemanticEvent.of(TX_LISTENER_EVENT_RECEIVED, hit.hitId()));
            case "TransactionalApplicationListenerSynchronization#processEventWithCallbacks" ->
                    List.of(SemanticEvent.of(TX_LISTENER_INVOKED, hit.hitId()));
            default -> List.of();
        };
    }

    private static boolean hasLocal(TraceEvent hit, String name) {
        return hit.locals().stream().anyMatch(local -> local.name().equals(name));
    }

    private static String simpleName(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedClassName : fullyQualifiedClassName.substring(lastDot + 1);
    }
}
