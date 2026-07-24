package lab.experiments.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanCreationFailureCacheTest {

    @Test
    @DisplayName("초기화 콜백에서 예외가 나면 그 빈은 싱글턴 캐시에 전혀 올라가지 않는다")
    void failedBeanNeverEntersTheSingletonCache() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("failingInitBean", FailingInitBean.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("failingInitBean");

        // DefaultSingletonBeanRegistry.getSingleton(name, ObjectFactory)는 singletonFactory.getObject()가
        // 예외 없이 끝나야만(newSingleton=true) addSingleton()을 호출한다 - 실패한 빈은 애초에
        // 캐시에 들어갈 기회조차 없다. 별도의 "실패 후 제거" 로직이 필요 없는 이유다.
        assertThat(context.getBeanFactory().containsSingleton("failingInitBean")).isFalse();
    }
}
