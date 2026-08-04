package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import java.util.ArrayList;
import java.util.List;
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
 */
public final class BeanLifecycleInterpreter implements ScenarioInterpreter {

    public static final String BEAN_CREATION_STARTED = "BEAN_CREATION_STARTED";
    public static final String SINGLETON_FACTORY_REGISTERED = "SINGLETON_FACTORY_REGISTERED";
    public static final String EARLY_REFERENCE_REQUESTED = "EARLY_REFERENCE_REQUESTED";
    public static final String EARLY_REFERENCE_PROXIED = "EARLY_REFERENCE_PROXIED";
    public static final String PROPERTY_INJECTION_STARTED = "PROPERTY_INJECTION_STARTED";
    public static final String BEAN_INITIALIZATION_STARTED = "BEAN_INITIALIZATION_STARTED";

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String className = simpleName(hit.location().className());
        String methodName = hit.location().methodName();
        List<SemanticEvent> events = new ArrayList<>();

        switch (className + "#" + methodName) {
            case "AbstractAutowireCapableBeanFactory#createBean" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(BEAN_CREATION_STARTED, hit, name)));
            case "DefaultSingletonBeanRegistry#addSingletonFactory" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(SINGLETON_FACTORY_REGISTERED, hit, name)));
            case "AbstractAutowireCapableBeanFactory#getEarlyBeanReference" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(EARLY_REFERENCE_REQUESTED, hit, name)));
            case "AbstractAutoProxyCreator#getEarlyBeanReference" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(EARLY_REFERENCE_PROXIED, hit, name)));
            case "AbstractAutowireCapableBeanFactory#populateBean" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(PROPERTY_INJECTION_STARTED, hit, name)));
            case "AbstractAutowireCapableBeanFactory#initializeBean" ->
                    beanName(hit).ifPresent(name -> events.add(withBeanName(BEAN_INITIALIZATION_STARTED, hit, name)));
            default -> {
                // 이 시나리오가 관심 없는 히트(예: getSingleton의 순수 캐시 조회) - 무시한다.
            }
        }
        return events;
    }

    private static SemanticEvent withBeanName(String type, TraceEvent hit, String beanName) {
        return SemanticEvent.of(type, hit.hitId(), "beanName", beanName);
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
