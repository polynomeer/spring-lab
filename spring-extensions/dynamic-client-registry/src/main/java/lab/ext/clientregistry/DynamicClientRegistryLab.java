package lab.ext.clientregistry;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DynamicClientRegistryLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = buildContext("external-clients.yml");

        for (ExternalApiClient client : context.getBean(ExternalApiClientConsumer.class).getClients()) {
            System.out.println(client);
        }
        System.out.println("paymentClient bean role = "
                + context.getBeanFactory().getBeanDefinition("paymentClient").getRole());

        context.close();
    }

    static AnnotationConfigApplicationContext buildContext(String configLocation) {
        return buildContext(new ExternalClientRegistrar(configLocation), true);
    }

    static AnnotationConfigApplicationContext buildContext(ExternalClientRegistrar registrar, boolean allowOverriding) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setAllowBeanDefinitionOverriding(allowOverriding);
        context.register(ClientRegistryConfig.class);

        // ConfigRewriterLab(project 8)에서 확인했던 것과 같은 이유로 빈으로 등록한다 -
        // context.addBeanFactoryPostProcessor()로 넘기면 컴포넌트 스캔(ConfigurationClassPostProcessor)보다
        // 먼저 실행되긴 하지만, 여기서는 순서 자체보다 "동적으로 등록된 빈이 이후 일반 DI에
        // 정상적으로 참여하는지"가 관심사라 실행 시점 차이가 결과에 영향을 주지 않는다 - 다만
        // 이 저장소의 관례를 그대로 따른다.
        context.registerBean("externalClientRegistrar", ExternalClientRegistrar.class, () -> registrar);

        context.refresh();
        return context;
    }
}
