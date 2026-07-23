package lab.minispring.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * SimpleBeanFactory has no "currently in creation" guard (see docs/01-ioc-container/bean-factory-getbean.md,
 * section 10). This reproduces the consequence of a singleton-to-singleton circular
 * reference with nothing to catch it.
 *
 * mini-container can't do constructor injection yet (that's project 15), so the
 * circularity is faked by stashing the factory in a static field and having each
 * bean's own constructor call back into getBean() for the other.
 */
class SimpleBeanFactoryCircularReferenceTest {

    @Test
    @DisplayName("재진입 감지가 없으면 StackOverflowError가 재귀 단계마다 다시 감싸여 원인 체인이 수천 단계로 쌓인다")
    void circularReferenceWithNoReentryGuardBuriesTheRootCause() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        CircularA.factory = beanFactory;
        CircularB.factory = beanFactory;

        beanFactory.registerBeanDefinition("a", new BeanDefinition(CircularA.class));
        beanFactory.registerBeanDefinition("b", new BeanDefinition(CircularB.class));

        // getBean() 자체가 StackOverflowError를 던지는 게 아니다: 재귀 호출이 Constructor#newInstance
        // 경계를 거칠 때마다 BeanInstantiationException으로 다시 감싸이기 때문에, 바깥에서 보이는
        // 최종 예외는 그 재래핑이 반복된 결과다.
        Throwable outermost = catchThrowable(() -> beanFactory.getBean("a"));

        assertThat(outermost).isInstanceOf(BeanInstantiationException.class);

        int depth = 0;
        Throwable cursor = outermost;
        Throwable rootCause = outermost;
        while (cursor != null) {
            depth++;
            rootCause = cursor;
            cursor = cursor.getCause();
        }

        // Spring의 BeanCurrentlyInCreationException(9번 참고)은 재진입을 즉시 감지해 한 단계 만에
        // 실패한다. 여기서는 재진입 감지가 아예 없어서 스택이 바닥날 때까지 계속 재귀한다 —
        // 원인 체인 깊이가 수백~수천에 달하는 게 그 증거다.
        assertThat(depth).isGreaterThan(100);
        assertThat(rootCause).isInstanceOf(StackOverflowError.class);
    }

    private static class CircularA {
        static SimpleBeanFactory factory;
        @SuppressWarnings("unused")
        final CircularB b;

        CircularA() {
            this.b = (CircularB) factory.getBean("b");
        }
    }

    private static class CircularB {
        static SimpleBeanFactory factory;
        @SuppressWarnings("unused")
        final CircularA a;

        CircularB() {
            this.a = (CircularA) factory.getBean("a");
        }
    }
}
