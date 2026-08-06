package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * tools/jdi-tracer/specs/transaction-propagation-lab.txt의 히트를 semantic 이벤트로
 * 옮긴다. 판별 규칙은 전부 docs/14-transaction-propagation/transaction-propagation.md
 * 7.1절에서 32개 히트를 직접 관찰해 확인한 것이다.
 *
 * <p>{@code suspend()}/{@code resume()}은 "아무 트랜잭션도 없을 때"도 방어적으로
 * {@code transaction=null}로 호출된다 - 실제로 무언가를 떼어내는/되돌리는 호출만
 * {@code transaction} 지역 변수가 null이 아니다. 이 구분이 없으면 REQUIRED로 새
 * 트랜잭션을 시작할 때마다 "suspend가 일어났다"는 잘못된 신호를 내보내게 된다.
 *
 * <p>스윔레인 시각화(docs/plan/03-learning-dashboard-design.md 6.3절)가 "이 suspend/
 * resume/commit/rollback이 어느 레인(어느 @Transactional 메서드) 소속인가"를 알아야 해서,
 * {@code createTransactionIfNecessary}로 진입할 때마다 joinpointIdentification을 LIFO
 * 스택에 쌓고, commit/rollback으로 그 경계가 끝날 때 꺼낸다 - suspend/resume/commit/
 * rollback 이벤트에는 그 시점의 스택 맨 위(=지금 진행 중인 메서드 경계)를 함께 담는다.
 * 실제 32개 히트 트레이스 두 시나리오 모두에서 이 스택 모델이 정확히 맞아떨어지는 것을
 * 확인했다(TransactionPropagationInterpreterTest).
 */
public final class TransactionPropagationInterpreter implements ScenarioInterpreter {

    public static final String TX_STARTED = "TX_STARTED";
    public static final String TX_SUSPENDED = "TX_SUSPENDED";
    public static final String TX_RESUMED = "TX_RESUMED";
    public static final String TX_COMMITTED = "TX_COMMITTED";
    public static final String TX_ROLLED_BACK = "TX_ROLLED_BACK";

    private final Deque<String> activeJoinpoints = new ArrayDeque<>();

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        if ("TransactionAspectSupport".equals(className) && "createTransactionIfNecessary".equals(methodName)) {
            Optional<String> id = local(hit, "joinpointIdentification");
            id.ifPresent(activeJoinpoints::push);
            return id.map(v -> List.of(SemanticEvent.of(TX_STARTED, hit.hitId(), "joinpointIdentification", v)))
                    .orElse(List.of());
        }

        if ("AbstractPlatformTransactionManager".equals(className)) {
            switch (methodName) {
                case "suspend" -> {
                    return realTransactionArg(hit) ? List.of(withCurrentJoinpoint(TX_SUSPENDED, hit.hitId())) : List.of();
                }
                case "resume" -> {
                    return realTransactionArg(hit) ? List.of(withCurrentJoinpoint(TX_RESUMED, hit.hitId())) : List.of();
                }
                case "processCommit" -> {
                    SemanticEvent event = withCurrentJoinpoint(TX_COMMITTED, hit.hitId());
                    popIfPresent();
                    return List.of(event);
                }
                case "processRollback" -> {
                    String unexpected = local(hit, "unexpected").orElse("false");
                    Map<String, String> attributes = new LinkedHashMap<>();
                    if (!activeJoinpoints.isEmpty()) {
                        attributes.put("joinpointIdentification", activeJoinpoints.peek());
                    }
                    attributes.put("unexpected", unexpected);
                    SemanticEvent event = new SemanticEvent(TX_ROLLED_BACK, hit.hitId(), attributes);
                    popIfPresent();
                    return List.of(event);
                }
                default -> {
                    return List.of();
                }
            }
        }

        return List.of();
    }

    private SemanticEvent withCurrentJoinpoint(String type, int hitId) {
        return activeJoinpoints.isEmpty()
                ? SemanticEvent.of(type, hitId)
                : SemanticEvent.of(type, hitId, "joinpointIdentification", activeJoinpoints.peek());
    }

    private void popIfPresent() {
        if (!activeJoinpoints.isEmpty()) {
            activeJoinpoints.pop();
        }
    }

    // suspend(transaction)/resume(transaction, ...)의 첫 인자가 null이면 "떼어낼/되돌릴
    // 대상이 애초에 없다"는 방어적 호출이다(getTransaction()이 새 트랜잭션을 시작할 때마다
    // 무조건 거치는 경로) - 실제 트랜잭션 객체가 있을 때만 진짜 suspend/resume이다.
    private static boolean realTransactionArg(TraceEvent hit) {
        return local(hit, "transaction").map(value -> !"null".equals(value)).orElse(false);
    }

    private static Optional<String> local(TraceEvent hit, String name) {
        return hit.locals().stream()
                .filter(local -> name.equals(local.name()))
                .map(TraceEvent.Local::value)
                .map(TransactionPropagationInterpreter::unquote)
                .findFirst();
    }

    private static String simpleName(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedClassName : fullyQualifiedClassName.substring(lastDot + 1);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
