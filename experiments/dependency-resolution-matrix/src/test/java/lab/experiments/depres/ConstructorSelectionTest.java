package lab.experiments.depres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstructorSelectionTest {

    @Test
    void singleConstructorIsUsedWithoutAutowiredAnnotation() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(SingleConstructorBean.class);
        context.refresh();

        SingleConstructorBean bean = context.getBean(SingleConstructorBean.class);

        assertThat(bean.getDependency()).isNotNull();
        context.close();
    }

    @Test
    void autowiredConstructorIsSelectedAmongMultipleConstructors() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(AutowiredSelectedBean.class);
        context.refresh();

        AutowiredSelectedBean bean = context.getBean(AutowiredSelectedBean.class);

        assertThat(bean.getDependency()).isNotNull();
        context.close();
    }

    @Test
    void twoRequiredAutowiredConstructorsFailAtStartup() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(DoubleRequiredAutowiredBean.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("Invalid autowire-marked constructor");

        context.close();
    }

    @Test
    void optionalAutowiredConstructorFallsBackToDefaultWhenDependencyMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // Dependency 빈을 등록하지 않는다.
        context.registerBean(OptionalAutowiredBean.class);
        context.refresh();

        OptionalAutowiredBean bean = context.getBean(OptionalAutowiredBean.class);

        assertThat(bean.getDependency()).isNull();
        context.close();
    }

    @Test
    void optionalAutowiredConstructorIsUsedWhenDependencyPresent() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(OptionalAutowiredBean.class);
        context.refresh();

        OptionalAutowiredBean bean = context.getBean(OptionalAutowiredBean.class);

        assertThat(bean.getDependency()).isNotNull();
        context.close();
    }

    @Test
    void multipleConstructorsWithoutAutowiredIgnoresTheOtherConstructorEntirely() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(MultiConstructorNoAutowiredBean.class);
        context.refresh();

        MultiConstructorNoAutowiredBean bean = context.getBean(MultiConstructorNoAutowiredBean.class);

        // @Autowired가 하나도 없으면 후보 자체가 정해지지 않아 기본 생성자로 떨어진다 -
        // Dependency 빈이 등록돼 있어도 주입되지 않는다.
        assertThat(bean.getDependency()).isNull();
        context.close();
    }
}
