package lab.ext.rewriter;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ConfigRewriterLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = buildContext();

        System.out.println("prototypeCandidate scope = "
                + context.getBeanFactory().getBeanDefinition("prototypeCandidate").getScope());
        System.out.println("lazyCandidate lazyInit = "
                + context.getBeanFactory().getBeanDefinition("lazyCandidate").isLazyInit());
        System.out.println("notificationSender.channel = "
                + context.getBean(NotificationSender.class).getChannel());
        System.out.println("excludedService still registered? = "
                + context.getBeanFactory().containsBeanDefinition("excludedService"));
        System.out.println("primaryGreeter primary = "
                + context.getBeanFactory().getBeanDefinition("primaryGreeter").isPrimary());
        System.out.println("Greeter resolved by type = " + context.getBean(Greeter.class).greet());
        System.out.println("supportRoleCandidate role = "
                + context.getBeanFactory().getBeanDefinition("supportRoleCandidate").getRole());

        context.close();
    }

    static AnnotationConfigApplicationContext buildContext() {
        return buildContext(new ExclusionRegistryPostProcessor(), new BeanDefinitionAnnotationRewriter());
    }

    static AnnotationConfigApplicationContext buildContext(
            ExclusionRegistryPostProcessor exclusionProcessor,
            BeanDefinitionAnnotationRewriter rewriter) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(RewriterConfig.class);

        // exclusionProcessor는 반드시 빈으로 등록해야 한다: context.addBeanFactoryPostProcessor()로
        // 넘긴 인스턴스는 ConfigurationClassPostProcessor(컴포넌트 스캔을 실제로 수행하는 빈)보다도
        // 먼저 postProcessBeanDefinitionRegistry()가 호출된다 - 그 시점엔 @Excluded가 붙은
        // ExcludedService조차 아직 스캔되지 않아 등록소에 없다. 빈으로 등록해야 스캔이 끝난 뒤에
        // 실행되는 그룹에 들어간다. (처음엔 addBeanFactoryPostProcessor로 넘겼다가 실제로 제거가
        // 안 되는 걸 보고서야 이 순서를 알게 됐다.)
        context.registerBean("exclusionRegistryPostProcessor", ExclusionRegistryPostProcessor.class,
                () -> exclusionProcessor);

        // rewriter는 postProcessBeanFactory()만 쓰는데, 그건 등록소 변경이 전부 끝난 뒤 마지막에
        // 실행되므로 어느 방식으로 등록해도 상관없다.
        context.addBeanFactoryPostProcessor(rewriter);

        context.refresh();
        return context;
    }
}
