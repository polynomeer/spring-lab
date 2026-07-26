package lab.ext.rewriter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigRewriterTest {

    @Test
    void scopeIsRewrittenToPrototype() {
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        BeanDefinition definition = context.getBeanFactory().getBeanDefinition("prototypeCandidate");
        assertThat(definition.getScope()).isEqualTo(BeanDefinition.SCOPE_PROTOTYPE);
        assertThat(context.getBean("prototypeCandidate")).isNotSameAs(context.getBean("prototypeCandidate"));

        context.close();
    }

    @Test
    void lazyInitIsForcedAndBeanIsNotCreatedDuringRefresh() {
        LazyCandidate.constructorCalls.set(0);
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        assertThat(context.getBeanFactory().getBeanDefinition("lazyCandidate").isLazyInit()).isTrue();
        assertThat(LazyCandidate.constructorCalls).hasValue(0);

        context.getBean(LazyCandidate.class);

        assertThat(LazyCandidate.constructorCalls).hasValue(1);
        context.close();
    }

    @Test
    void defaultPropertyValueIsAppliedWhenMissing() {
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        assertThat(context.getBean(NotificationSender.class).getChannel()).isEqualTo("email");

        context.close();
    }

    @Test
    void excludedBeanIsRemovedFromRegistryBeforeRefreshFinishes() {
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        assertThat(context.getBeanFactory().containsBeanDefinition("excludedService")).isFalse();
        assertThatThrownBy(() -> context.getBean(ExcludedService.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        context.close();
    }

    @Test
    void primaryFlagResolvesTypeAmbiguityBetweenTwoCandidates() {
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        assertThat(context.getBeanFactory().getBeanDefinition("primaryGreeter").isPrimary()).isTrue();
        assertThat(context.getBean(Greeter.class).greet()).isEqualTo("primary");

        context.close();
    }

    @Test
    void roleIsChangedToSupport() {
        AnnotationConfigApplicationContext context = ConfigRewriterLab.buildContext();

        assertThat(context.getBeanFactory().getBeanDefinition("supportRoleCandidate").getRole())
                .isEqualTo(BeanDefinition.ROLE_SUPPORT);

        context.close();
    }

    @Test
    void registryPostProcessorRemovalHappensBeforeThePlainPostProcessorSeesTheBean() {
        BeanDefinitionAnnotationRewriter rewriter = new BeanDefinitionAnnotationRewriter();
        AnnotationConfigApplicationContext context =
                ConfigRewriterLab.buildContext(new ExclusionRegistryPostProcessor(), rewriter);

        // rewriter(일반 BeanFactoryPostProcessor)가 빈 이름 목록을 순회할 시점엔 이미
        // excludedService가 제거되고 없다 - BeanDefinitionRegistryPostProcessor의 등록소
        // 변경이 항상 먼저 끝난다는 걸 증명한다.
        assertThat(rewriter.getProcessedBeanNames()).doesNotContain("excludedService");

        context.close();
    }
}
