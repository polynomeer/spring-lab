package lab.experiments.refresh;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ContextRefreshOrderingTest {

    @BeforeEach
    void resetLog() {
        RefreshEventLog.reset();
    }

    @Test
    @DisplayName("BeanFactoryPostProcessor -> eager singleton 생성/후처리 -> ContextRefreshedEvent 순으로 실행된다")
    void refreshRunsExtensionPointsInOrder() {
        AnnotationConfigApplicationContext context = ContextRefreshVisualizerLab.buildContext();

        List<String> events = RefreshEventLog.events();

        assertThat(events).containsExactly(
                "BFPP:invoked",
                "constructor:eagerSingleton",
                "BPP:before:eagerSingleton",
                "BPP:after:eagerSingleton",
                "event:ContextRefreshedEvent:" + context.getDisplayName()
        );

        context.close();
    }

    @Test
    @DisplayName("lazy singleton은 refresh() 동안 생성되지 않고, 최초 getBean() 때 생성된다")
    void lazySingletonIsNotCreatedUntilFirstGetBean() {
        AnnotationConfigApplicationContext context = ContextRefreshVisualizerLab.buildContext();

        assertThat(RefreshEventLog.events()).doesNotContain("constructor:lazySingleton");

        context.getBean(LazySingleton.class);

        assertThat(RefreshEventLog.events()).contains("constructor:lazySingleton");

        context.close();
    }

    @Test
    @DisplayName("prototype 빈은 refresh() 동안 생성되지 않고, getBean()을 호출할 때마다 새로 생성된다")
    void prototypeBeanIsCreatedOnlyOnDemandAndEveryTime() {
        AnnotationConfigApplicationContext context = ContextRefreshVisualizerLab.buildContext();

        assertThat(RefreshEventLog.events()).doesNotContain("constructor:prototypeBean");

        PrototypeBean first = context.getBean(PrototypeBean.class);
        PrototypeBean second = context.getBean(PrototypeBean.class);

        assertThat(first).isNotSameAs(second);
        assertThat(RefreshEventLog.events().stream().filter("constructor:prototypeBean"::equals).count())
                .isEqualTo(2);

        context.close();
    }
}
