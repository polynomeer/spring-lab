package lab.experiments.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextLifecycleTest {

    @Test
    @DisplayName("refresh() 이전에 getBean()을 호출하면 '아직 refresh되지 않았다'는 예외가 발생한다")
    void getBeanBeforeRefreshThrows() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(EagerSingleton.class);

        assertThatThrownBy(() -> context.getBean(EagerSingleton.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been refreshed yet");
    }

    @Test
    @DisplayName("close() 이후 getBean()을 호출하면 '이미 닫혔다'는 예외가 발생한다")
    void getBeanAfterCloseThrows() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(EagerSingleton.class);
        context.refresh();
        context.close();

        assertThatThrownBy(() -> context.getBean(EagerSingleton.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has been closed already");
    }

    @Test
    @DisplayName("AnnotationConfigApplicationContext는 refresh()를 두 번 호출할 수 없다")
    void secondRefreshThrows() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(EagerSingleton.class);
        context.refresh();

        assertThatThrownBy(context::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not support multiple refresh attempts");

        context.close();
    }

    @Test
    @DisplayName("초기화 중 예외가 발생하면 컨텍스트는 active 상태가 되지 못하고, 이후 조회도 실패한다")
    void refreshFailureLeavesContextInactive() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(FailingBean.class);

        assertThatThrownBy(context::refresh).isInstanceOf(BeanCreationException.class);

        // cancelRefresh()는 active 플래그만 내리고 closed는 세우지 않으므로, 메시지는
        // "닫혔다"가 아니라 "아직 refresh되지 않았다"로 나온다 - refresh를 시도했다가 실패했는데도.
        assertThatThrownBy(() -> context.getBean(FailingBean.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been refreshed yet");
    }
}
