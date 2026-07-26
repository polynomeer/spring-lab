package lab.experiments.depres;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class InjectionFormTest {

    @Test
    void optionalInjectionIsEmptyWhenNoCandidateExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(OptionalInjectionBean.class);
        context.refresh();

        assertThat(context.getBean(OptionalInjectionBean.class).getDependency()).isEmpty();
        context.close();
    }

    @Test
    void optionalInjectionIsPresentWhenCandidateExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(OptionalInjectionBean.class);
        context.refresh();

        assertThat(context.getBean(OptionalInjectionBean.class).getDependency()).isPresent();
        context.close();
    }

    @Test
    void listInjectionCollectsEveryCandidateOfTheType() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(PrimaryGreeter.class);
        context.registerBean(SecondaryGreeter.class);
        context.registerBean(OtherGreeter.class);
        context.registerBean(ListInjectionBean.class);
        context.refresh();

        assertThat(context.getBean(ListInjectionBean.class).getGreeters())
                .hasSize(3)
                .extracting(Greeter::greet)
                .containsExactlyInAnyOrder("primary", "secondary", "other");
        context.close();
    }

    @Test
    void listInjectionWithNoCandidatesIsEmptyNotAnError() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ListInjectionBean.class);
        context.refresh();

        assertThat(context.getBean(ListInjectionBean.class).getGreeters()).isEmpty();
        context.close();
    }

    @Test
    void objectProviderResolvesLazilyOnDemand() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Dependency.class);
        context.registerBean(ObjectProviderInjectionBean.class);
        context.refresh();

        ObjectProviderInjectionBean bean = context.getBean(ObjectProviderInjectionBean.class);

        assertThat(bean.getDependencyProvider().getObject()).isNotNull();
        context.close();
    }

    @Test
    void objectProviderWithNoCandidateReturnsNullInsteadOfThrowing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectProviderInjectionBean.class);
        context.refresh();

        ObjectProviderInjectionBean bean = context.getBean(ObjectProviderInjectionBean.class);

        assertThat(bean.getDependencyProvider().getIfAvailable()).isNull();
        context.close();
    }
}
