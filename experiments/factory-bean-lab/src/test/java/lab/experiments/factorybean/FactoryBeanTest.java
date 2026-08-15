package lab.experiments.factorybean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryBeanTest {

    @Test
    void getBeanByNameReturnsTheProductNotTheFactory() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SingletonFactoryBeanConfig.class)) {
            Object bean = context.getBean("widget");

            assertThat(bean).isInstanceOf(Widget.class);
        }
    }

    @Test
    void getBeanWithAmpersandPrefixReturnsTheFactoryItself() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SingletonFactoryBeanConfig.class)) {
            Object bean = context.getBean("&widget");

            assertThat(bean).isInstanceOf(SingletonProductFactoryBean.class);
        }
    }

    @Test
    void singletonFactoryBeanCachesTheProductAndCallsGetObjectOnlyOnce() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SingletonFactoryBeanConfig.class)) {
            Widget first = (Widget) context.getBean("widget");
            Widget second = (Widget) context.getBean("widget");
            SingletonProductFactoryBean factory =
                    (SingletonProductFactoryBean) context.getBean("&widget");

            // FactoryBeanRegistrySupport#getObjectFromFactoryBean()는 factory.isSingleton()이
            // true면 factoryBeanObjectCache에 저장해 두고 재사용한다 - getObject()가 실제로
            // 몇 번 실행됐는지로 직접 증명한다.
            assertThat(first.id()).isEqualTo(second.id());
            assertThat(factory.getObjectCallCount()).isEqualTo(1);
        }
    }

    @Test
    void nonSingletonFactoryBeanCreatesAFreshProductEveryCallButTheFactoryBeanItselfIsStillASingleton() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(NonSingletonFactoryBeanConfig.class)) {
            Widget first = (Widget) context.getBean("widget");
            Widget second = (Widget) context.getBean("widget");
            NonSingletonProductFactoryBean factoryFromFirstLookup =
                    (NonSingletonProductFactoryBean) context.getBean("&widget");
            NonSingletonProductFactoryBean factoryFromSecondLookup =
                    (NonSingletonProductFactoryBean) context.getBean("&widget");

            // isSingleton() = false이므로 캐시를 완전히 건너뛰고 매번 getObject()를 새로
            // 실행한다.
            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(factoryFromFirstLookup.getObjectCallCount()).isEqualTo(2);

            // 하지만 이 팩토리 "빈 자신"은 여전히 평범한 싱글턴 스코프다 - "&widget"을 몇
            // 번을 조회해도 같은 팩토리 인스턴스다. isSingleton()은 산출물에 대한 스위치일
            // 뿐, 팩토리 자신의 스코프와는 별개다.
            assertThat(factoryFromFirstLookup).isSameAs(factoryFromSecondLookup);
        }
    }

    @Test
    void autowiredFieldDeclaredAsTheProductTypeReceivesTheProduct() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AutowiringConfig.class)) {
            WidgetConsumer consumer = context.getBean(WidgetConsumer.class);

            assertThat(consumer.product()).isInstanceOf(Widget.class);
        }
    }

    @Test
    void autowiredFieldDeclaredAsFactoryBeanTypeReceivesTheFactoryItself() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AutowiringConfig.class)) {
            WidgetConsumer consumer = context.getBean(WidgetConsumer.class);
            FactoryBean<?> factoryFromContainer = (FactoryBean<?>) context.getBean("&widget");

            // getObjectType()이 Widget.class를 미리 알려주기 때문에, 오토와이어링 후보
            // 판정 시점에 getObject()를 호출해 실제로 인스턴스화하지 않고도 두 필드 타입을
            // 구분할 수 있다 - 같은 빈 정의 하나가 선언된 필드 타입에 따라 다르게 해석된다.
            assertThat(consumer.factory()).isSameAs(factoryFromContainer);
        }
    }
}
