package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 입력은 docs/12-auto-proxy-creator/auto-proxy-creator.md 7.1절이 기록한, 실제
// AutoProxyCreationLab jdi-tracer 세션(38개 히트)의 핵심 구간을 그대로 옮긴 것이다:
// 첫 대상 빈(autoProxyTimingConfig) 평가 중 advisor(timingAdvisor)가 재귀적으로
// 인스턴스화되고, 이후 legacyReport(프록시됨) → notificationServiceImpl(스킵) →
// orderServiceImpl(프록시됨) 순으로 진행된다.
class AutoProxyInterpreterTest {

    private static final String AUTO_PROXY_CREATOR = "org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator";

    @Test
    void translatesTheRealAutoProxyCreationLabHitSequenceIntoSemanticEvents() {
        AutoProxyInterpreter interpreter = new AutoProxyInterpreter();

        List<TraceEvent> hits = List.of(
                wrapIfNecessary(2, "autoProxyTimingConfig", false),
                postProcessAfterInitialization(7, "timingAdvisor", true),
                wrapIfNecessary(8, "timingAdvisor", true),
                wrapIfNecessary(12, "legacyReport", false),
                createProxy(19, "legacyReport"),
                wrapIfNecessary(22, "notificationServiceImpl", false),
                wrapIfNecessary(30, "orderServiceImpl", false),
                createProxy(37, "orderServiceImpl"));

        List<SemanticEvent> events = hits.stream().flatMap(hit -> interpreter.onHit(hit).stream()).toList();

        assertThat(events)
                .extracting(SemanticEvent::type, e -> e.attributes().get("beanName"), SemanticEvent::sourceHitId)
                .containsExactly(
                        tuple(AutoProxyInterpreter.ADVISOR_LOOKUP_STARTED, "autoProxyTimingConfig", 2),
                        tuple(AutoProxyInterpreter.ADVISOR_EAGERLY_INSTANTIATED, "timingAdvisor", 7),
                        tuple(AutoProxyInterpreter.PROXY_SKIPPED, "autoProxyTimingConfig", 2),
                        tuple(AutoProxyInterpreter.ADVISOR_LOOKUP_STARTED, "legacyReport", 12),
                        tuple(AutoProxyInterpreter.PROXY_CREATED, "legacyReport", 19),
                        tuple(AutoProxyInterpreter.ADVISOR_LOOKUP_STARTED, "notificationServiceImpl", 22),
                        tuple(AutoProxyInterpreter.PROXY_SKIPPED, "notificationServiceImpl", 22),
                        tuple(AutoProxyInterpreter.ADVISOR_LOOKUP_STARTED, "orderServiceImpl", 30),
                        tuple(AutoProxyInterpreter.PROXY_CREATED, "orderServiceImpl", 37));
    }

    private static TraceEvent wrapIfNecessary(int hitId, String beanName, boolean nestedInAdvisorLookup) {
        return hit(hitId, AUTO_PROXY_CREATOR, "wrapIfNecessary", beanName, nestedInAdvisorLookup);
    }

    private static TraceEvent postProcessAfterInitialization(int hitId, String beanName, boolean nestedInAdvisorLookup) {
        return hit(hitId, AUTO_PROXY_CREATOR, "postProcessAfterInitialization", beanName, nestedInAdvisorLookup);
    }

    private static TraceEvent createProxy(int hitId, String beanName) {
        return hit(hitId, AUTO_PROXY_CREATOR, "createProxy", beanName, false);
    }

    private static TraceEvent hit(int hitId, String className, String methodName, String beanName, boolean nested) {
        List<TraceEvent.Frame> stack = nested
                ? List.of(
                        new TraceEvent.Frame(className, methodName),
                        new TraceEvent.Frame("org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator", "findCandidateAdvisors"),
                        new TraceEvent.Frame("org.springframework.aop.framework.autoproxy.BeanFactoryAdvisorRetrievalHelper", "findAdvisorBeans"))
                : List.of(new TraceEvent.Frame(className, methodName));
        return new TraceEvent(
                hitId,
                hitId,
                "main",
                new TraceEvent.Location(className, methodName, 1),
                stack,
                List.of(new TraceEvent.Local("beanName", "java.lang.String", "\"" + beanName + "\"")),
                true);
    }
}
