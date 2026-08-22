package lab.experiments.overriding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanDefinitionOverridingTest {

    @Test
    void byDefaultTheLastRegisteredDefinitionSilentlyWins() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ConfigA.class, ConfigB.class);
            context.refresh();

            // 예외도, 경고성 로그 이상의 그 어떤 신호도 없이 컨텍스트가 정상적으로 뜬다 -
            // ConfigB가 나중에 등록됐다는 이유만으로 ConfigA의 "greeting" 정의를 조용히
            // 대체한다.
            Greeting greeting = context.getBean(Greeting.class);
            assertThat(greeting.message()).isEqualTo("from-B");
        }
    }

    @Test
    void swappingRegistrationOrderFlipsWhichDefinitionWins() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 두 설정 클래스 자체는 그대로다 - register()에 넘기는 순서만 뒤집었다.
            context.register(ConfigB.class, ConfigA.class);
            context.refresh();

            Greeting greeting = context.getBean(Greeting.class);
            assertThat(greeting.message()).isEqualTo("from-A");
        }
    }

    @Test
    void disablingOverridingThrowsBeanDefinitionOverrideException() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
            beanFactory.setAllowBeanDefinitionOverriding(false);
            context.register(ConfigA.class, ConfigB.class);

            // 다른 감쌈 없이 BeanDefinitionOverrideException 자체가 refresh() 밖으로
            // 그대로 전파된다 - registerBeanDefinition()이 이 예외를 던지는 지점과
            // 호출자 사이에 추가로 포장하는 코드가 없다는 뜻이다.
            assertThatThrownBy(context::refresh).isInstanceOf(BeanDefinitionOverrideException.class);
        }
    }

    @Test
    void theExceptionIdentifiesTheConflictingBeanNameAndBothDefinitions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
            beanFactory.setAllowBeanDefinitionOverriding(false);
            context.register(ConfigA.class, ConfigB.class);

            BeanDefinitionOverrideException ex = (BeanDefinitionOverrideException)
                    org.assertj.core.api.Assertions.catchThrowable(context::refresh);

            assertThat(ex.getBeanName()).isEqualTo("greeting");
            // 새로 등록하려던 쪽(ConfigB)과 이미 있던 쪽(ConfigA)을 정확히 구분해서
            // 담고 있다 - 각 BeanDefinition의 factoryBeanName으로 어느 설정 클래스에서
            // 왔는지 알 수 있다.
            assertThat(ex.getBeanDefinition().getFactoryBeanName()).isEqualTo("configB");
            assertThat(ex.getExistingDefinition().getFactoryBeanName()).isEqualTo("configA");
        }
    }
}
