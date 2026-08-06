package lab.dashboard.interpret;

import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 입력 히트 시퀀스는 실제로 CircularDependencyLab을 jdi-tracer로 라이브 실행해서 얻은
// 20개 히트 중 순환 참조가 실제로 풀리는 마지막 구간(15~20번)을 그대로 옮긴 것이다 -
// docs/plan/03-learning-dashboard-design.md 8번 절이 요구하는 "원본 히트 시퀀스를
// 입력으로, 기대하는 semantic 이벤트 시퀀스를 출력으로 검증".
class BeanLifecycleInterpreterTest {

    @Test
    void translatesTheRealCircularDependencyLabHitSequenceIntoSemanticEvents() {
        BeanLifecycleInterpreter interpreter = new BeanLifecycleInterpreter();

        String singletonRegistry = "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry";
        String beanFactory = "org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory";
        String autoProxyCreator = "org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator";

        List<SemanticEvent> events = List.of(
                hit(15, singletonRegistry, "addSingletonFactory", local("beanName", "\"proxiedCircularA\"")),
                hit(16, beanFactory, "populateBean", local("beanName", "\"proxiedCircularA\"")),
                hit(17, singletonRegistry, "addSingletonFactory", local("beanName", "\"proxiedCircularB\"")),
                hit(18, beanFactory, "populateBean", local("beanName", "\"proxiedCircularB\"")),
                hit(19, beanFactory, "getEarlyBeanReference", local("beanName", "\"proxiedCircularA\"")),
                hit(20, autoProxyCreator, "getEarlyBeanReference", local("beanName", "\"proxiedCircularA\"")))
                .stream()
                .flatMap(hit -> interpreter.onHit(hit).stream())
                .toList();

        assertThat(events).extracting(SemanticEvent::type).containsExactly(
                BeanLifecycleInterpreter.SINGLETON_FACTORY_REGISTERED,
                BeanLifecycleInterpreter.PROPERTY_INJECTION_STARTED,
                BeanLifecycleInterpreter.SINGLETON_FACTORY_REGISTERED,
                BeanLifecycleInterpreter.PROPERTY_INJECTION_STARTED,
                BeanLifecycleInterpreter.EARLY_REFERENCE_REQUESTED,
                BeanLifecycleInterpreter.EARLY_REFERENCE_PROXIED);

        // populateBean(proxiedCircularB)이 proxiedCircularA를 조기 참조로 요청하게 만들고,
        // 그 요청이 AbstractAutoProxyCreator에 의해 프록시로 바뀐다는 것 - 이게 바로
        // "조기 참조 == 최종 프록시"(docs/10-primary-qualifier-circular)의 관찰 지점이다.
        // requestedBy가 "proxiedCircularB"인 것은, 그래프 시각화의 엣지(B → A)가 바로 이
        // 속성에서 나온다는 뜻이다.
        assertThat(events.get(4).attributes()).containsEntry("beanName", "proxiedCircularA").containsEntry("requestedBy", "proxiedCircularB");
        assertThat(events.get(5).attributes()).containsEntry("beanName", "proxiedCircularA").containsEntry("requestedBy", "proxiedCircularB");
        assertThat(events.get(4).sourceHitId()).isEqualTo(19);
        assertThat(events.get(5).sourceHitId()).isEqualTo(20);
    }

    @Test
    void ignoresHitsOutsideThisScenariosVocabulary() {
        BeanLifecycleInterpreter interpreter = new BeanLifecycleInterpreter();

        List<SemanticEvent> events = interpreter.onHit(
                hit(1, "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry", "getSingleton",
                        local("beanName", "\"paymentService\"")));

        assertThat(events).isEmpty();
    }

    private static TraceEvent hit(int hitId, String className, String methodName, TraceEvent.Local... locals) {
        return new TraceEvent(
                hitId,
                hitId, // timestampNanos - 테스트에서는 순서만 중요하므로 hitId를 그대로 재사용
                "main",
                new TraceEvent.Location(className, methodName, 1),
                List.of(),
                List.of(locals),
                true);
    }

    private static TraceEvent.Local local(String name, String value) {
        return new TraceEvent.Local(name, "java.lang.String", value);
    }
}
