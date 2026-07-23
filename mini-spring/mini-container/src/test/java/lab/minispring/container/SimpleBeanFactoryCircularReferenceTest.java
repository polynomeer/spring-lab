package lab.minispring.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mini-container can't do constructor injection yet (that's project 15), so circularity
 * is faked by stashing the factory in a static field and having each bean's own
 * constructor call back into getBean() for the other.
 */
class SimpleBeanFactoryCircularReferenceTest {

    @Test
    @DisplayName("순환 참조를 재진입 시점에 CircularDependencyException으로 즉시 감지한다")
    void circularReferenceIsDetectedOnReentry() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        CircularA.factory = beanFactory;
        CircularB.factory = beanFactory;

        beanFactory.registerBeanDefinition("a", new BeanDefinition(CircularA.class));
        beanFactory.registerBeanDefinition("b", new BeanDefinition(CircularB.class));

        assertThatThrownBy(() -> beanFactory.getBean("a"))
                .isInstanceOf(CircularDependencyException.class)
                .hasMessageContaining("a -> b -> a");
    }

    @Test
    @DisplayName("순환이 아닌 의존 체인은 재진입 감지에 걸리지 않고 정상 생성된다")
    void nonCircularChainStillResolves() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        ChainRoot.factory = beanFactory;

        beanFactory.registerBeanDefinition("leaf", new BeanDefinition(ChainLeaf.class));
        beanFactory.registerBeanDefinition("root", new BeanDefinition(ChainRoot.class));

        ChainRoot root = (ChainRoot) beanFactory.getBean("root");

        assertThat(root.leaf).isNotNull();
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

    private static class ChainLeaf {
    }

    private static class ChainRoot {
        static SimpleBeanFactory factory;
        final ChainLeaf leaf;

        ChainRoot() {
            this.leaf = (ChainLeaf) factory.getBean("leaf");
        }
    }
}
