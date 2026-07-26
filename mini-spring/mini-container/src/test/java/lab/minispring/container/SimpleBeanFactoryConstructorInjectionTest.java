package lab.minispring.container;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleBeanFactoryConstructorInjectionTest {

    @Test
    void singleConstructorIsAutowiredWithoutAnyAnnotation() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new BeanDefinition(Dependency.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(SingleConstructorConsumer.class));

        SingleConstructorConsumer consumer = (SingleConstructorConsumer) beanFactory.getBean("consumer");

        assertThat(consumer.dependency).isNotNull();
        assertThat(consumer.dependency).isSameAs(beanFactory.getBean("dependency"));
    }

    @Test
    void miniAutowiredConstructorIsSelectedAmongMultiple() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new BeanDefinition(Dependency.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(AutowiredSelectedConsumer.class));

        AutowiredSelectedConsumer consumer = (AutowiredSelectedConsumer) beanFactory.getBean("consumer");

        assertThat(consumer.dependency).isNotNull();
    }

    @Test
    void twoMiniAutowiredConstructorsAreAmbiguous() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new BeanDefinition(Dependency.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(DoubleAutowiredConsumer.class));

        assertThatThrownBy(() -> beanFactory.getBean("consumer"))
                .isInstanceOf(AmbiguousConstructorException.class);
    }

    @Test
    void multipleConstructorsWithoutAnnotationFallBackToNoArgConstructor() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new BeanDefinition(Dependency.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(UnannotatedMultiConstructorConsumer.class));

        UnannotatedMultiConstructorConsumer consumer =
                (UnannotatedMultiConstructorConsumer) beanFactory.getBean("consumer");

        // 실제 Spring과 같은 결론(9주차 문서 참고): 후보를 정하지 못하면 기본 생성자로 떨어지고,
        // Dependency 빈이 등록돼 있어도 무시된다.
        assertThat(consumer.dependency).isNull();
    }

    @Test
    void multipleConstructorsWithoutAnnotationOrNoArgFallbackAreAmbiguous() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new BeanDefinition(Dependency.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(NoFallbackMultiConstructorConsumer.class));

        assertThatThrownBy(() -> beanFactory.getBean("consumer"))
                .isInstanceOf(AmbiguousConstructorException.class);
    }

    @Test
    void miniQualifierResolvesAmbiguousCandidateByName() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("greeterA", new BeanDefinition(GreeterA.class));
        beanFactory.registerBeanDefinition("greeterB", new BeanDefinition(GreeterB.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(QualifiedConsumer.class));

        QualifiedConsumer consumer = (QualifiedConsumer) beanFactory.getBean("consumer");

        assertThat(consumer.greeter).isInstanceOf(GreeterB.class);
    }

    @Test
    void primaryBeanDefinitionResolvesAmbiguousCandidateWithoutQualifier() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("greeterA", new BeanDefinition(GreeterA.class).asPrimary());
        beanFactory.registerBeanDefinition("greeterB", new BeanDefinition(GreeterB.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(UnqualifiedConsumer.class));

        UnqualifiedConsumer consumer = (UnqualifiedConsumer) beanFactory.getBean("consumer");

        assertThat(consumer.greeter).isInstanceOf(GreeterA.class);
    }

    @Test
    void ambiguousCandidateWithoutPrimaryOrQualifierThrows() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("greeterA", new BeanDefinition(GreeterA.class));
        beanFactory.registerBeanDefinition("greeterB", new BeanDefinition(GreeterB.class));
        beanFactory.registerBeanDefinition("consumer", new BeanDefinition(UnqualifiedConsumer.class));

        assertThatThrownBy(() -> beanFactory.getBean("consumer"))
                .isInstanceOf(NoUniqueBeanException.class);
    }

    @Test
    void constructorCircularReferenceIsDetected() {
        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.registerBeanDefinition("a", new BeanDefinition(CircularA.class));
        beanFactory.registerBeanDefinition("b", new BeanDefinition(CircularB.class));

        assertThatThrownBy(() -> beanFactory.getBean("a"))
                .isInstanceOf(CircularDependencyException.class)
                .hasMessageContaining("a -> b -> a");
    }

    private static class Dependency {
    }

    private static class SingleConstructorConsumer {

        final Dependency dependency;

        SingleConstructorConsumer(Dependency dependency) {
            this.dependency = dependency;
        }
    }

    private static class AutowiredSelectedConsumer {

        final Dependency dependency;

        AutowiredSelectedConsumer() {
            this.dependency = null;
        }

        @MiniAutowired
        AutowiredSelectedConsumer(Dependency dependency) {
            this.dependency = dependency;
        }
    }

    private static class DoubleAutowiredConsumer {

        @MiniAutowired
        DoubleAutowiredConsumer() {
        }

        @MiniAutowired
        DoubleAutowiredConsumer(Dependency dependency) {
        }
    }

    private static class UnannotatedMultiConstructorConsumer {

        final Dependency dependency;

        UnannotatedMultiConstructorConsumer() {
            this.dependency = null;
        }

        UnannotatedMultiConstructorConsumer(Dependency dependency) {
            this.dependency = dependency;
        }
    }

    private static class NoFallbackMultiConstructorConsumer {

        NoFallbackMultiConstructorConsumer(Dependency dependency) {
        }

        NoFallbackMultiConstructorConsumer(Dependency dependency, String label) {
        }
    }

    private interface Greeter {
    }

    private static class GreeterA implements Greeter {
    }

    private static class GreeterB implements Greeter {
    }

    private static class QualifiedConsumer {

        final Greeter greeter;

        QualifiedConsumer(@MiniQualifier("greeterB") Greeter greeter) {
            this.greeter = greeter;
        }
    }

    private static class UnqualifiedConsumer {

        final Greeter greeter;

        UnqualifiedConsumer(Greeter greeter) {
            this.greeter = greeter;
        }
    }

    private static class CircularA {

        CircularA(CircularB b) {
        }
    }

    private static class CircularB {

        CircularB(CircularA a) {
        }
    }
}
