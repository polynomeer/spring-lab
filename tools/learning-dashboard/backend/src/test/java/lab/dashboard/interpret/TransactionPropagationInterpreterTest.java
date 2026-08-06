package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 입력은 docs/14-transaction-propagation/transaction-propagation.md 7.1절이 기록한, 실제
// TransactionPropagationLab jdi-tracer 세션(32개 히트)의 두 시나리오를 그대로 옮긴 것이다.
class TransactionPropagationInterpreterTest {

    private static final String ASPECT_SUPPORT = "org.springframework.transaction.interceptor.TransactionAspectSupport";
    private static final String TX_MANAGER = "org.springframework.transaction.support.AbstractPlatformTransactionManager";

    @Test
    void requiresNewSuspendsTheOuterConnectionAndResumesItInsideTheInnerCommit() {
        TransactionPropagationInterpreter interpreter = new TransactionPropagationInterpreter();

        List<TraceEvent> hits = List.of(
                createTransactionIfNecessary(3, "lab.experiments.tx.OrderServiceImpl.placeOrderInnerRequiresNew"),
                suspend(5, null), // getTransaction()의 방어적 suspend(null) - 무시돼야 한다
                createTransactionIfNecessary(8, "lab.experiments.tx.PaymentServiceImpl.payRequiresNew"),
                suspend(10, "DataSourceTransactionObject"), // 진짜 suspend
                processCommit(13),
                resume(14, "DataSourceTransactionObject"),
                processCommit(17));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        String outer = "lab.experiments.tx.OrderServiceImpl.placeOrderInnerRequiresNew";
        String inner = "lab.experiments.tx.PaymentServiceImpl.payRequiresNew";

        // 스윔레인 시각화가 "이 이벤트가 어느 레인 소속인가"를 알 수 있도록, suspend/resume/
        // commit도 그 시점에 진행 중이던 joinpointIdentification을 함께 실어 보낸다 - 특히
        // hit14의 resume은 inner의 커밋 정리 절차 안에서 일어나지만, 실제로 다시 활성화되는
        // 건 outer이므로 outer로 귀속돼야 한다(LIFO 스택이 hit13에서 이미 inner를 pop한 뒤라
        // 정확히 그렇게 된다).
        assertThat(events)
                .extracting(SemanticEvent::type, SemanticEvent::sourceHitId, e -> e.attributes().get("joinpointIdentification"))
                .containsExactly(
                        tuple(TransactionPropagationInterpreter.TX_STARTED, 3, outer),
                        tuple(TransactionPropagationInterpreter.TX_STARTED, 8, inner),
                        tuple(TransactionPropagationInterpreter.TX_SUSPENDED, 10, inner),
                        tuple(TransactionPropagationInterpreter.TX_COMMITTED, 13, inner),
                        tuple(TransactionPropagationInterpreter.TX_RESUMED, 14, outer),
                        tuple(TransactionPropagationInterpreter.TX_COMMITTED, 17, outer));
    }

    @Test
    void requiredParticipantFailureSurfacesAsAnUnexpectedRollbackAtOuterCommit() {
        TransactionPropagationInterpreter interpreter = new TransactionPropagationInterpreter();

        List<TraceEvent> hits = List.of(
                createTransactionIfNecessary(20, "lab.experiments.tx.OrderServiceImpl.placeOrderCatchingInnerRequiredFailure"),
                suspend(22, null), // 역시 방어적 호출 - 무시돼야 한다
                createTransactionIfNecessary(25, "lab.experiments.tx.PaymentServiceImpl.payRequired"),
                processRollback(29, false), // 참여자의 rollback() - 아직 "예상된" 롤백
                processRollback(32, true)); // 바깥의 commit() 호출이 실제로는 여기로 샌 것

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        String outer = "lab.experiments.tx.OrderServiceImpl.placeOrderCatchingInnerRequiredFailure";
        String inner = "lab.experiments.tx.PaymentServiceImpl.payRequired";

        assertThat(events)
                .extracting(SemanticEvent::type, e -> e.attributes().get("unexpected"),
                        e -> e.attributes().get("joinpointIdentification"), SemanticEvent::sourceHitId)
                .containsExactly(
                        tuple(TransactionPropagationInterpreter.TX_STARTED, null, outer, 20),
                        tuple(TransactionPropagationInterpreter.TX_STARTED, null, inner, 25),
                        tuple(TransactionPropagationInterpreter.TX_ROLLED_BACK, "false", inner, 29),
                        tuple(TransactionPropagationInterpreter.TX_ROLLED_BACK, "true", outer, 32));
    }

    private static TraceEvent createTransactionIfNecessary(int hitId, String joinpointIdentification) {
        return hit(hitId, ASPECT_SUPPORT, "createTransactionIfNecessary",
                new TraceEvent.Local("joinpointIdentification", "java.lang.String", "\"" + joinpointIdentification + "\""));
    }

    private static TraceEvent suspend(int hitId, String transactionSummary) {
        return hit(hitId, TX_MANAGER, "suspend",
                new TraceEvent.Local("transaction", "java.lang.Object", transactionSummary == null ? "null" : "instance of " + transactionSummary));
    }

    private static TraceEvent resume(int hitId, String transactionSummary) {
        return hit(hitId, TX_MANAGER, "resume",
                new TraceEvent.Local("transaction", "java.lang.Object", "instance of " + transactionSummary),
                new TraceEvent.Local("resourcesHolder", "java.lang.Object", "instance of SuspendedResourcesHolder"));
    }

    private static TraceEvent processCommit(int hitId) {
        return hit(hitId, TX_MANAGER, "processCommit",
                new TraceEvent.Local("status", "java.lang.Object", "instance of DefaultTransactionStatus"));
    }

    private static TraceEvent processRollback(int hitId, boolean unexpected) {
        return hit(hitId, TX_MANAGER, "processRollback",
                new TraceEvent.Local("status", "java.lang.Object", "instance of DefaultTransactionStatus"),
                new TraceEvent.Local("unexpected", "boolean", String.valueOf(unexpected)));
    }

    private static TraceEvent hit(int hitId, String className, String methodName, TraceEvent.Local... locals) {
        return new TraceEvent(
                hitId, hitId, "main",
                new TraceEvent.Location(className, methodName, 1),
                List.of(new TraceEvent.Frame(className, methodName)),
                List.of(locals),
                true);
    }
}
