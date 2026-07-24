package lab.experiments.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParentChildContextTest {

    @BeforeEach
    void resetLog() {
        RefreshEventLog.reset();
    }

    @Test
    @DisplayName("자식은 부모의 빈을 조회할 수 있지만, 부모는 자식의 빈을 조회할 수 없다")
    void childSeesParentBeansButNotViceVersa() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentConfig.class);

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.register(ChildConfig.class);
        child.refresh();

        assertThat(child.getBean(ParentOnlyBean.class)).isNotNull();
        assertThat(child.getBean(ChildOnlyBean.class)).isNotNull();

        assertThatThrownBy(() -> parent.getBean(ChildOnlyBean.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        child.close();
        parent.close();
    }

    @Test
    @DisplayName("동일 이름 빈이 양쪽에 있으면 각 컨텍스트는 자기 자신의 정의를 우선 사용한다")
    void sameNameBeanResolvesToOwnDefinitionFirst() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentConfig.class);

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.register(ChildConfig.class);
        child.refresh();

        assertThat(child.getBean("shared", SharedBean.class).getSource()).isEqualTo("child");
        assertThat(parent.getBean("shared", SharedBean.class).getSource()).isEqualTo("parent");

        child.close();
        parent.close();
    }

    @Test
    @DisplayName("자식에서 발행된 이벤트는 부모의 리스너에도 전파된다")
    void eventsPublishedInChildPropagateToParentListeners() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
        parent.register(ParentConfig.class, LoggingApplicationListener.class);
        parent.refresh();

        int eventsAfterParentRefresh = RefreshEventLog.events().size();
        assertThat(eventsAfterParentRefresh).isEqualTo(1); // parent 자신의 ContextRefreshedEvent

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.register(ChildConfig.class);
        child.refresh();

        // 부모 리스너는 부모 자신의 이벤트 + 자식으로부터 전파된 이벤트, 총 두 번 호출된다.
        assertThat(RefreshEventLog.events()).hasSize(eventsAfterParentRefresh + 1);

        child.close();
        parent.close();
    }
}
