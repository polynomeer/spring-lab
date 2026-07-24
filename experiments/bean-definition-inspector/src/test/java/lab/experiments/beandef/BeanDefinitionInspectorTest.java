package lab.experiments.beandef;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class BeanDefinitionInspectorTest {

    @Test
    @DisplayName("@Component로 등록된 빈은 factoryBean/factoryMethod가 없다")
    void componentScannedBeanHasNoFactoryMethod() {
        BeanDefinitionSummary summary = summaryOf("notificationService");

        assertThat(summary.beanClassName()).isEqualTo(NotificationService.class.getName());
        assertThat(summary.scope()).isEqualTo("singleton");
        assertThat(summary.factoryBeanName()).isNull();
        assertThat(summary.factoryMethodName()).isNull();
        assertThat(summary.hasInstanceSupplier()).isFalse();
        assertThat(summary.role()).isEqualTo(BeanDefinition.ROLE_APPLICATION);
    }

    @Test
    @DisplayName("@Bean 메서드로 등록된 빈은 beanClass가 비어 있고 factoryBean/factoryMethod로만 표현된다")
    void beanMethodHasFactoryBeanAndMethodInsteadOfBeanClass() {
        BeanDefinitionSummary summary = summaryOf("paymentService");

        // ConfigurationClassBeanDefinitionReader는 @Bean 메서드의 반환 타입을 beanClass로 미리
        // 못박지 않는다 - factoryBeanName + factoryMethodName만으로 표현하고, 실제 타입은 생성 시점에
        // 메서드를 리플렉션으로 조사해서 알아낸다.
        assertThat(summary.beanClassName()).isNull();
        assertThat(summary.factoryBeanName()).isEqualTo("appConfig");
        assertThat(summary.factoryMethodName()).isEqualTo("paymentService");
    }

    @Test
    @DisplayName("registerBeanDefinition()으로 직접 등록한 빈은 factoryBean/factoryMethod가 없다")
    void manuallyRegisteredBeanHasNoFactoryMethod() {
        BeanDefinitionSummary summary = summaryOf("manualBean");

        assertThat(summary.beanClassName()).isEqualTo(ManualBean.class.getName());
        assertThat(summary.factoryBeanName()).isNull();
        assertThat(summary.factoryMethodName()).isNull();
    }

    @Test
    @DisplayName("정적 팩토리 메서드 기반 빈은 factoryBean 없이 factoryMethod만 가리킨다")
    void staticFactoryMethodBeanHasFactoryMethodButNoFactoryBean() {
        BeanDefinitionSummary summary = summaryOf("legacyClient");

        assertThat(summary.beanClassName()).isEqualTo(LegacyClient.class.getName());
        assertThat(summary.factoryBeanName()).isNull();
        assertThat(summary.factoryMethodName()).isEqualTo("create");
    }

    @Test
    @DisplayName("Supplier 기반 빈은 factoryMethod 없이 instanceSupplier만 가지고 있다")
    void supplierBasedBeanHasInstanceSupplierButNoFactoryMethod() {
        BeanDefinitionSummary summary = summaryOf("supplierBean");

        assertThat(summary.beanClassName()).isEqualTo(SupplierBean.class.getName());
        assertThat(summary.hasInstanceSupplier()).isTrue();
        assertThat(summary.factoryMethodName()).isNull();
    }

    @Test
    @DisplayName("@Configuration(proxyBeanMethods=true, 기본값) 클래스의 beanClass는 CGLIB 서브클래스로 바뀐다")
    void configurationClassBeanClassIsReplacedByCglibSubclass() {
        BeanDefinitionSummary summary = summaryOf("appConfig");

        assertThat(summary.beanClassName()).isNotEqualTo(AppConfig.class.getName());
        assertThat(summary.beanClassName()).contains("AppConfig$$SpringCGLIB$$");
    }

    @Test
    @DisplayName("Spring 내부 인프라 빈은 ROLE_INFRASTRUCTURE로 등록된다")
    void infrastructureBeansAreMarkedWithInfrastructureRole() {
        Map<String, BeanDefinitionSummary> byName = summariesByName();

        List<String> infrastructureBeanNames = List.of(
                "org.springframework.context.annotation.internalConfigurationAnnotationProcessor",
                "org.springframework.context.annotation.internalAutowiredAnnotationProcessor",
                "org.springframework.context.event.internalEventListenerProcessor",
                "org.springframework.context.event.internalEventListenerFactory"
        );

        for (String beanName : infrastructureBeanNames) {
            assertThat(byName).containsKey(beanName);
            assertThat(byName.get(beanName).role()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
        }
    }

    @Test
    @DisplayName("jakarta.annotation-api가 클래스패스에 없으면 internalCommonAnnotationProcessor는 아예 등록되지 않는다")
    void commonAnnotationProcessorIsAbsentWithoutJakartaAnnotationApi() {
        Map<String, BeanDefinitionSummary> byName = summariesByName();

        assertThat(byName).doesNotContainKey(
                "org.springframework.context.annotation.internalCommonAnnotationProcessor");
    }

    private BeanDefinitionSummary summaryOf(String beanName) {
        BeanDefinitionSummary summary = summariesByName().get(beanName);
        if (summary == null) {
            throw new AssertionError("no BeanDefinition found for " + beanName);
        }
        return summary;
    }

    private Map<String, BeanDefinitionSummary> summariesByName() {
        AnnotationConfigApplicationContext context = BeanDefinitionInspectorLab.buildContext();
        try {
            return BeanDefinitionInspector.inspect(context).stream()
                    .collect(Collectors.toMap(BeanDefinitionSummary::beanName, summary -> summary));
        } finally {
            context.close();
        }
    }
}
