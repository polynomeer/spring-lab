package lab.minispring.container;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleBeanFactoryLifecycleTest {

    @Test
    void beanPostProcessorsRunBeforeAndAfterInitializingBean() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        List<String> order = new ArrayList<>();

        // afterPropertiesSet이 실제로 before와 after "사이"에 실행되는지는 getBean()이 반환한
        // 뒤에 확인해선 알 수 없다(그때는 이미 다 끝난 뒤다) - 각 후처리기 호출 시점에 그 순간의
        // afterPropertiesSetCalled 값을 같이 기록해야 순서를 증명할 수 있다.
        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                order.add("before:afterPropertiesSetCalled=" + ((RecordingBean) bean).afterPropertiesSetCalled);
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                order.add("after:afterPropertiesSetCalled=" + ((RecordingBean) bean).afterPropertiesSetCalled);
                return bean;
            }
        });
        beanFactory.registerBeanDefinition("recording", new BeanDefinition(RecordingBean.class));

        RecordingBean bean = (RecordingBean) beanFactory.getBean("recording");

        assertThat(order).containsExactly(
                "before:afterPropertiesSetCalled=false",
                "after:afterPropertiesSetCalled=true"
        );
        assertThat(bean.afterPropertiesSetCalled).isTrue();
    }

    @Test
    void beanPostProcessorCanReplaceTheBeanEntirely() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        String replacement = "replaced";

        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return replacement;
            }
        });
        beanFactory.registerBeanDefinition("recording", new BeanDefinition(RecordingBean.class));

        assertThat(beanFactory.getBean("recording")).isSameAs(replacement);
    }

    @Test
    void disposableBeanDestroyIsCalledOnDestroySingletons() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("disposable", new BeanDefinition(DisposableTarget.class));
        DisposableTarget bean = (DisposableTarget) beanFactory.getBean("disposable");

        beanFactory.destroySingletons();

        assertThat(bean.destroyed).isTrue();
        assertThat(beanFactory.containsBean("disposable")).isTrue(); // BeanDefinition은 남는다

        // singletonObjects는 비워졌지만 BeanDefinition은 남아 있어서, 다시 조회하면 새 인스턴스가 생성된다.
        DisposableTarget recreated = (DisposableTarget) beanFactory.getBean("disposable");
        assertThat(recreated).isNotSameAs(bean);
        assertThat(recreated.destroyed).isFalse();
    }

    @Test
    void initializationFailureIsNeverCachedAsASingleton() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("failing", new BeanDefinition(FailingInitBean.class));

        assertThatThrownBy(() -> beanFactory.getBean("failing")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> beanFactory.getBean("failing")).isInstanceOf(IllegalStateException.class);

        // getBean()이 실패한 인스턴스를 캐시하지 않았다면, 매번 새로 생성자를 호출한다.
        assertThat(FailingInitBean.constructorCalls).hasValue(2);
    }

    private static class RecordingBean implements InitializingBean {

        boolean afterPropertiesSetCalled = false;

        @Override
        public void afterPropertiesSet() {
            afterPropertiesSetCalled = true;
        }
    }

    private static class DisposableTarget implements DisposableBean {

        boolean destroyed = false;

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private static class FailingInitBean implements InitializingBean {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        FailingInitBean() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void afterPropertiesSet() {
            throw new IllegalStateException("afterPropertiesSet boom");
        }
    }
}
