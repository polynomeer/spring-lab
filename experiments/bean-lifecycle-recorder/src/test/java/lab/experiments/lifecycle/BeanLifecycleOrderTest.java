package lab.experiments.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class BeanLifecycleOrderTest {

    @BeforeEach
    void resetLog() {
        LifecycleEventLog.reset();
    }

    @Test
    @DisplayName("생성부터 초기화까지: InstantiationAwareBPP -> 생성자 -> @Autowired -> Aware -> BPP -> " +
            "@PostConstruct -> afterPropertiesSet -> custom init -> BPP -> SmartInitializingSingleton -> 이벤트")
    void recordsFullInitializationOrder() {
        AnnotationConfigApplicationContext context = BeanLifecycleRecorderLab.buildContext();

        assertThat(LifecycleEventLog.events()).containsExactly(
                "InstantiationAwareBPP:beforeInstantiation",
                "constructor",
                "InstantiationAwareBPP:afterInstantiation",
                "@Autowired",
                "BeanNameAware",
                "BeanFactoryAware",
                "ApplicationContextAware",
                "BPP:beforeInitialization",
                "@PostConstruct",
                "afterPropertiesSet",
                "customInitMethod",
                "BPP:afterInitialization",
                "SmartInitializingSingleton:afterSingletonsInstantiated",
                "event:ContextRefreshedEvent"
        );

        LifecycleTarget target = context.getBean(LifecycleTarget.class);
        assertThat(target.getDependency()).isNotNull();

        context.close();
    }

    @Test
    @DisplayName("소멸: close() 이후 @PreDestroy -> destroy -> custom destroy 순으로 실행된다")
    void recordsDestructionOrderOnClose() {
        AnnotationConfigApplicationContext context = BeanLifecycleRecorderLab.buildContext();
        int eventsBeforeClose = LifecycleEventLog.events().size();

        context.close();

        assertThat(LifecycleEventLog.events()).hasSize(eventsBeforeClose + 3);
        assertThat(LifecycleEventLog.events()).endsWith(
                "@PreDestroy",
                "destroy",
                "customDestroyMethod"
        );
    }
}
