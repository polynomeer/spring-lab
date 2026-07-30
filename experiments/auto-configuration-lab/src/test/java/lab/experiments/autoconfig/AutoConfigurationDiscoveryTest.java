package lab.experiments.autoconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// @EnableAutoConfiguration은 SpringApplication이 아니라 순수 @Import(AutoConfigurationImportSelector)
// 메커니즘이다 - 그래서 SpringApplication.run() 없이 평범한 AnnotationConfigApplicationContext로
// 구동해도 자동 설정 탐색이 그대로 동작한다는 것부터 확인한다.
class AutoConfigurationDiscoveryTest {

    @BeforeEach
    void reset() {
        RegistrationOrder.reset();
    }

    @Test
    void autoConfigurationCandidatesAreDiscoveredFromTheImportsFile() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(PlainAppConfig.class)) {
            assertThat(context.getBean(GreetingService.class)).isInstanceOf(DefaultGreetingService.class);
            assertThat(context.getBean(AuditLog.class)).isNotNull();
        }
    }

    @Test
    void afterDeclarationOverridesTheOrderWrittenInTheImportsFile() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(PlainAppConfig.class)) {
            String[] beanDefinitionNames = context.getBeanFactory().getBeanDefinitionNames();
            int loggingIndex = indexOf(beanDefinitionNames, LoggingSupportAutoConfiguration.class.getName());
            int greetingIndex = indexOf(beanDefinitionNames, GreetingAutoConfiguration.class.getName());

            // .imports 파일에는 GreetingAutoConfiguration이 먼저 적혀 있지만(파일 참고),
            // @AutoConfiguration(after = ...) 선언이 실제 BeanDefinition 등록 순서를 뒤집는다.
            assertThat(loggingIndex).isGreaterThanOrEqualTo(0);
            assertThat(greetingIndex).isGreaterThanOrEqualTo(0);
            assertThat(loggingIndex).isLessThan(greetingIndex);

            // 서로 의존관계가 없는데도, 인스턴스화 순서 역시 같은 순서를 따른다.
            assertThat(RegistrationOrder.order()).containsExactly(
                    "LoggingSupportAutoConfiguration", "GreetingAutoConfiguration");
        }
    }

    @Test
    void userDefinedBeanMakesTheAutoConfigurationBackOff() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfigWithUserGreetingService.class)) {
            assertThat(context.getBean(GreetingService.class)).isInstanceOf(CustomGreetingService.class);
        }
    }

    private int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
