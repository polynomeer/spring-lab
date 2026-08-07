package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 jdi-tracer 세션(experiments/application-event-lab의 ApplicationEventLab)에서 관찰한
// 히트 시퀀스를 그대로 옮겨 검증한다 - docs/21-application-events.md 7번 절 참고. 그 세션에서
// TransactionalApplicationListenerMethodAdapter#processEventWithCallback이라는 문서상 메서드가
// 실제로는 존재하지 않고 TransactionalApplicationListenerSynchronization#processEventWithCallbacks
// (복수형, 다른 클래스)라는 걸 발견해 문서도 함께 고쳤다.
class EventMulticastInterpreterTest {

    @Test
    void publishEventOverloadCascadeIsDedupedToOneEventPerRealPublish() {
        EventMulticastInterpreter interpreter = new EventMulticastInterpreter();

        // publishEvent(Object)가 내부적으로 publishEvent(Object, ResolvableType)를 그대로
        // 호출한다 - 실제 관찰대로 typeHint 로컬이 있는 두 번째 히트에서만 이벤트가 나가야 한다.
        List<TraceEvent> hits = List.of(
                hitWithLocals(1, "org.springframework.context.support.AbstractApplicationContext", "publishEvent",
                        List.of(new TraceEvent.Local("event", "java.lang.Object", "instance of OrderCompletedEvent"))),
                hitWithLocals(2, "org.springframework.context.support.AbstractApplicationContext", "publishEvent",
                        List.of(
                                new TraceEvent.Local("event", "java.lang.Object", "instance of OrderCompletedEvent"),
                                new TraceEvent.Local("typeHint", "org.springframework.core.ResolvableType", "null"))));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(EventMulticastInterpreter.EVENT_PUBLISHED);
        assertThat(events.get(0).sourceHitId()).isEqualTo(2);
    }

    @Test
    void listenerInvocationCarriesItsRealObservedThreadName() {
        EventMulticastInterpreter interpreter = new EventMulticastInterpreter();

        // invokeListener() 자체는 동기/비동기 모두 발행자 스레드(main)에서 호출된다(실제
        // jdi-tracer 세션으로 확인됨 - 클래스 Javadoc 참고) - 스레드 전환은 asyncListener()
        // 자체의 히트에서만 보인다.
        List<SemanticEvent> invokeEvents = interpreter.onHit(hitOnThread(3,
                "org.springframework.context.event.SimpleApplicationEventMulticaster", "invokeListener", "main"));
        List<SemanticEvent> asyncEvents = interpreter.onHit(hitOnThread(4,
                "lab.experiments.event.OrderEventListeners", "asyncListener", "SimpleAsyncTaskExecutor-1"));

        assertThat(invokeEvents.get(0).attributes()).containsEntry("thread", "main");
        assertThat(asyncEvents).extracting(SemanticEvent::type).containsExactly(EventMulticastInterpreter.ASYNC_LISTENER_EXECUTING);
        assertThat(asyncEvents.get(0).attributes()).containsEntry("thread", "SimpleAsyncTaskExecutor-1");
    }

    @Test
    void translatesTheRestOfTheDocumentedFlowIntoSemanticEvents() {
        EventMulticastInterpreter interpreter = new EventMulticastInterpreter();

        List<TraceEvent> hits = List.of(
                hit(5, "org.springframework.context.event.SimpleApplicationEventMulticaster", "multicastEvent"),
                hit(6, "org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter", "onApplicationEvent"),
                hit(7, "org.springframework.transaction.event.TransactionalApplicationListenerSynchronization", "processEventWithCallbacks"));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(
                EventMulticastInterpreter.MULTICAST_STARTED,
                EventMulticastInterpreter.TX_LISTENER_EVENT_RECEIVED,
                EventMulticastInterpreter.TX_LISTENER_INVOKED);
    }

    @Test
    void ignoresHitsOutsideThisScenariosVocabulary() {
        EventMulticastInterpreter interpreter = new EventMulticastInterpreter();

        List<SemanticEvent> events = interpreter.onHit(
                hit(8, "org.springframework.context.event.ApplicationListenerMethodAdapter", "processEvent"));

        assertThat(events).isEmpty();
    }

    private static TraceEvent hit(int hitId, String className, String methodName) {
        return hitOnThread(hitId, className, methodName, "main");
    }

    private static TraceEvent hitOnThread(int hitId, String className, String methodName, String thread) {
        return new TraceEvent(
                hitId, hitId, thread,
                new TraceEvent.Location(className, methodName, 1),
                List.of(new TraceEvent.Frame(className, methodName)),
                List.of(),
                true);
    }

    private static TraceEvent hitWithLocals(int hitId, String className, String methodName, List<TraceEvent.Local> locals) {
        return new TraceEvent(
                hitId, hitId, "main",
                new TraceEvent.Location(className, methodName, 1),
                List.of(new TraceEvent.Frame(className, methodName)),
                locals,
                true);
    }
}
