package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * tools/jdi-tracer/specs/auto-proxy-creation-lab.txt의 히트를, "AOP 자동 프록시 생성"
 * 시나리오(docs/plan/03-learning-dashboard-design.md 6.2절)의 semantic 이벤트로 옮긴다.
 * 매핑 규칙은 전부 docs/12-auto-proxy-creator/auto-proxy-creator.md 7.1절에서 38개
 * 히트를 직접 관찰해 확인한 것을 코드로 옮긴 것이다.
 *
 * <p>JDI 브레이크포인트가 메서드 진입 지점에서만 걸린다는 한계 때문에 두 가지는 일부러
 * 하지 않는다: {@code canApply()}의 반환값(true/false)은 알 수 없어 이벤트로 만들지
 * 않고(대신 그 결과는 뒤이어 {@code createProxy}가 호출되는지 여부로 간접 관찰한다),
 * {@code PROXY_CREATED}에 JDK/CGLIB 종류를 담지 않는다(그 결정은
 * {@code DefaultAopProxyFactory#createAopProxy} 내부에서 일어나고 진입 시점 지역 변수로는
 * 드러나지 않는다).
 */
public final class AutoProxyInterpreter implements ScenarioInterpreter {

    public static final String ADVISOR_LOOKUP_STARTED = "ADVISOR_LOOKUP_STARTED";
    public static final String ADVISOR_EAGERLY_INSTANTIATED = "ADVISOR_EAGERLY_INSTANTIATED";
    public static final String PROXY_CREATED = "PROXY_CREATED";
    public static final String PROXY_SKIPPED = "PROXY_SKIPPED";

    private record PendingBean(String beanName, int hitId) {
    }

    private PendingBean pending;

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();
        List<SemanticEvent> events = new ArrayList<>();

        if ("AbstractAutoProxyCreator".equals(className) && "postProcessAfterInitialization".equals(methodName)
                && isNestedInsideAdvisorLookup(hit)) {
            // findAdvisorBeans()가 아직 인스턴스화되지 않은 Advisor 빈을 그 자리에서 getBean()으로
            // 조회하며 재귀적으로 이 훅을 다시 타는 경우 - 7.1절 히트 #7이 이 지점이다.
            beanName(hit).ifPresent(name -> events.add(SemanticEvent.of(ADVISOR_EAGERLY_INSTANTIATED, hit.hitId(), "beanName", name)));
            return events;
        }

        if ("AbstractAutoProxyCreator".equals(className) && "wrapIfNecessary".equals(methodName)) {
            if (isNestedInsideAdvisorLookup(hit)) {
                // Advisor 빈 자신의 wrapIfNecessary(isInfrastructureClass가 곧 조용히 반환시킬
                // 호출) - 최상위 평가 흐름을 어지럽히지 않도록 별도 이벤트 없이 지나간다.
                return events;
            }
            beanName(hit).ifPresent(name -> {
                if (pending != null) {
                    // 이전에 평가하던 빈에 대해 createProxy가 한 번도 오지 않은 채 다음 빈으로
                    // 넘어왔다 - canApply가 전부 false였거나 isInfrastructureClass였다는 뜻.
                    events.add(SemanticEvent.of(PROXY_SKIPPED, pending.hitId(), "beanName", pending.beanName()));
                }
                pending = new PendingBean(name, hit.hitId());
                events.add(SemanticEvent.of(ADVISOR_LOOKUP_STARTED, hit.hitId(), "beanName", name));
            });
            return events;
        }

        if ("AbstractAutoProxyCreator".equals(className) && "createProxy".equals(methodName)) {
            beanName(hit).ifPresent(name -> {
                events.add(SemanticEvent.of(PROXY_CREATED, hit.hitId(), "beanName", name));
                if (pending != null && pending.beanName().equals(name)) {
                    pending = null;
                }
            });
        }

        return events;
    }

    private static boolean isNestedInsideAdvisorLookup(TraceEvent hit) {
        return hit.stack().stream().anyMatch(frame -> "findAdvisorBeans".equals(frame.methodName()));
    }

    private static Optional<String> beanName(TraceEvent hit) {
        return hit.locals().stream()
                .filter(local -> "beanName".equals(local.name()))
                .map(local -> unquote(local.value()))
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
