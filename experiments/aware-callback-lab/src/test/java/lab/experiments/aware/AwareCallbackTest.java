package lab.experiments.aware;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class AwareCallbackTest {

    @Test
    void allTenAwareCallbacksFireInAFixedOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AwareConfig.class)) {
            RecordingAwareBean bean = context.getBean(RecordingAwareBean.class);

            // 앞의 세 개(직접 호출, invokeAwareMethods)가 뒤의 일곱 개(BeanPostProcessor
            // 호출, ApplicationContextAwareProcessor)보다 항상 먼저다 - initializeBean()
            // 소스에서 invokeAwareMethods()가 applyBeanPostProcessorsBeforeInitialization()
            // 보다 먼저 호출되기 때문이다.
            assertThat(bean.invocationOrder()).containsExactly(
                    "BeanNameAware",
                    "BeanClassLoaderAware",
                    "BeanFactoryAware",
                    "EnvironmentAware",
                    "EmbeddedValueResolverAware",
                    "ResourceLoaderAware",
                    "ApplicationEventPublisherAware",
                    "MessageSourceAware",
                    "ApplicationStartupAware",
                    "ApplicationContextAware");
        }
    }

    @Test
    void beanFactoryAwareReceivesTheConcreteInternalBeanFactoryInstance() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AwareConfig.class)) {
            RecordingAwareBean bean = context.getBean(RecordingAwareBean.class);

            // "AbstractAutowireCapableBeanFactory.this"를 그대로 넘긴다 - 제한된 뷰나
            // 별도 래퍼가 아니라 컨테이너가 실제로 쓰는 그 BeanFactory 객체 자신이다.
            assertThat(bean.beanFactory()).isSameAs(context.getBeanFactory());
        }
    }

    @Test
    void applicationContextAwareReceivesTheContextItself() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AwareConfig.class)) {
            RecordingAwareBean bean = context.getBean(RecordingAwareBean.class);

            assertThat(bean.applicationContext()).isSameAs(context);
        }
    }

    @Test
    void aUserDefinedBeanPostProcessorBeanAlsoReceivesApplicationContextAware() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AwareConfig.class)) {
            CustomAwareBeanPostProcessor postProcessor = context.getBean(CustomAwareBeanPostProcessor.class);

            // ApplicationContextAwareProcessor는 prepareBeanFactory()(refresh()의 2번째
            // 단계)에서 이미 등록돼 있다 - 사용자 정의 BeanPostProcessor 빈은 그보다 훨씬
            // 나중 단계(registerBeanPostProcessors())에서 만들어지므로, 그 자신도
            // ApplicationContextAwareProcessor의 적용 대상이 된다.
            assertThat(postProcessor.applicationContext()).isSameAs(context);
        }
    }
}
