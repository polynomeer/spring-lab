package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * tools/jdi-tracer/specs/bean-factory-lab.txt(순차적 빈 생성)와
 * circular-dependency-lab.txt(3단계 캐시 + AOP 프록시 조기 결정) 두 스펙 모두에서 나오는
 * 히트를 같은 시나리오("빈 생명주기 + 순환 참조", docs/plan/03-learning-dashboard-design.md
 * 6.1절)의 semantic 이벤트로 옮긴다.
 *
 * <p>JDI 브레이크포인트는 메서드 진입 지점에서만 걸린다는 한계가 있다(exit 이벤트를 아직
 * 지원하지 않는다) - 그래서 "빈이 완전히 초기화됐다"는 사실은 이 해석기가 만들어내지
 * 않는다. 실제로 관찰 가능한 사실(생성 시작, 3차 캐시 등록, 조기 참조 요청/프록시화,
 * 프로퍼티 주입 시작, 초기화 시작)만 옮긴다.
 *
 * <p>그래프 시각화가 "누가 누구를 의존하는가"(엣지)를 그리려면 조기 참조 요청이 "어느 빈을
 * 채우던 도중" 일어났는지가 필요하다 - {@code populateBean}이 마지막으로 지나간 빈 이름을
 * 기억해 뒀다가, 그 직후 {@code getEarlyBeanReference}가 오면 {@code requestedBy}로 함께
 * 실어 보낸다. 실제 CircularDependencyLab 20개 히트 트레이스에서 이 순서(B를 채우다가 A의
 * 조기 참조를 요청)가 정확히 그렇게 일어나는 것을 확인했다(BeanLifecycleInterpreterTest).
 */
public final class BeanLifecycleInterpreter implements ScenarioInterpreter {

    public static final String BEAN_CREATION_STARTED = "BEAN_CREATION_STARTED";
    public static final String SINGLETON_FACTORY_REGISTERED = "SINGLETON_FACTORY_REGISTERED";
    public static final String EARLY_REFERENCE_REQUESTED = "EARLY_REFERENCE_REQUESTED";
    public static final String EARLY_REFERENCE_PROXIED = "EARLY_REFERENCE_PROXIED";
    public static final String PROPERTY_INJECTION_STARTED = "PROPERTY_INJECTION_STARTED";
    public static final String BEAN_INITIALIZATION_STARTED = "BEAN_INITIALIZATION_STARTED";

    private String currentlyPopulatingBean;

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();

        return switch (className + "#" + methodName) {
            case "AbstractAutowireCapableBeanFactory#createBean" ->
                    beanName(hit).map(name -> List.of(withBeanName(BEAN_CREATION_STARTED, hit, name))).orElse(List.of());
            case "DefaultSingletonBeanRegistry#addSingletonFactory" ->
                    beanName(hit).map(name -> List.of(withBeanName(SINGLETON_FACTORY_REGISTERED, hit, name))).orElse(List.of());
            case "AbstractAutowireCapableBeanFactory#getEarlyBeanReference" ->
                    beanName(hit).map(name -> List.of(withRequestedBy(EARLY_REFERENCE_REQUESTED, hit, name))).orElse(List.of());
            case "AbstractAutoProxyCreator#getEarlyBeanReference" ->
                    beanName(hit).map(name -> List.of(withRequestedBy(EARLY_REFERENCE_PROXIED, hit, name))).orElse(List.of());
            case "AbstractAutowireCapableBeanFactory#populateBean" -> {
                Optional<String> name = beanName(hit);
                name.ifPresent(value -> currentlyPopulatingBean = value);
                yield name.map(value -> List.of(withBeanName(PROPERTY_INJECTION_STARTED, hit, value))).orElse(List.of());
            }
            case "AbstractAutowireCapableBeanFactory#initializeBean" ->
                    beanName(hit).map(name -> List.of(withBeanName(BEAN_INITIALIZATION_STARTED, hit, name))).orElse(List.of());
            // 이 시나리오가 관심 없는 히트(예: getSingleton의 순수 캐시 조회) - 무시한다.
            default -> List.of();
        };
    }

    private static SemanticEvent withBeanName(String type, TraceEvent hit, String beanName) {
        return SemanticEvent.of(type, hit.hitId(), "beanName", beanName);
    }

    private SemanticEvent withRequestedBy(String type, TraceEvent hit, String beanName) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("beanName", beanName);
        if (currentlyPopulatingBean != null && !currentlyPopulatingBean.equals(beanName)) {
            attributes.put("requestedBy", currentlyPopulatingBean);
        }
        return new SemanticEvent(type, hit.hitId(), attributes);
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
