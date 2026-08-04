package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.List;
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
 */
public final class TransactionPropagationInterpreter implements ScenarioInterpreter {

    public static final String TX_STARTED = "TX_STARTED";
    public static final String TX_SUSPENDED = "TX_SUSPENDED";
    public static final String TX_RESUMED = "TX_RESUMED";
    public static final String TX_COMMITTED = "TX_COMMITTED";
    public static final String TX_ROLLED_BACK = "TX_ROLLED_BACK";

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        if ("TransactionAspectSupport".equals(className) && "createTransactionIfNecessary".equals(methodName)) {
            return local(hit, "joinpointIdentification")
                    .map(id -> List.of(SemanticEvent.of(TX_STARTED, hit.hitId(), "joinpointIdentification", id)))
                    .orElse(List.of());
        }

        if ("AbstractPlatformTransactionManager".equals(className)) {
            switch (methodName) {
                case "suspend" -> {
                    return realTransactionArg(hit) ? List.of(SemanticEvent.of(TX_SUSPENDED, hit.hitId())) : List.of();
                }
                case "resume" -> {
                    return realTransactionArg(hit) ? List.of(SemanticEvent.of(TX_RESUMED, hit.hitId())) : List.of();
                }
                case "processCommit" -> {
                    return List.of(SemanticEvent.of(TX_COMMITTED, hit.hitId()));
                }
                case "processRollback" -> {
                    String unexpected = local(hit, "unexpected").orElse("false");
                    return List.of(SemanticEvent.of(TX_ROLLED_BACK, hit.hitId(), "unexpected", unexpected));
                }
                default -> {
                    return List.of();
                }
            }
        }

        return List.of();
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
