package lab.experiments.importselector;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ImportMechanismTest {

    @Test
    void plainImportRegistersTheConcreteConfigurationClassAsIs() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScenarioAConfig.class)) {
            assertThat(context.getBean(Greeting.class).message()).isEqualTo("plain");
        }
    }

    @Test
    void importSelectorIncludesOnlyTheConfigForTheRequestedLanguage() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ScenarioBEnOnlyConfig.class)) {
            List<String> messages = context.getBeansOfType(Greeting.class).values().stream()
                    .map(Greeting::message)
                    .toList();

            // languages = "en" 하나만 줬으므로 GreetingImportSelector는
            // EnglishGreetingConfig의 클래스 이름만 돌려준다 - KoreanGreetingConfig는
            // 아예 파싱 대상에 오르지도 않는다.
            assertThat(messages).containsExactly("Hello");
        }
    }

    @Test
    void importSelectorIncludesBothConfigsWhenBothLanguagesAreRequested() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ScenarioBEnAndKoConfig.class)) {
            List<String> messages = context.getBeansOfType(Greeting.class).values().stream()
                    .map(Greeting::message)
                    .toList();

            // @EnableGreeting(languages = {"en", "ko"})가 셀렉터에 전달하는
            // AnnotationMetadata의 속성값만 바뀌었을 뿐인데, 실제로 파싱되는 @Configuration
            // 클래스 집합 자체가 달라진다.
            assertThat(messages).containsExactlyInAnyOrder("Hello", "안녕하세요");
        }
    }

    @Test
    void importBeanDefinitionRegistrarCreatesADynamicNumberOfBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScenarioCConfig.class)) {
            List<String> messages = context.getBeansOfType(Greeting.class).values().stream()
                    .map(Greeting::message)
                    .sorted()
                    .toList();

            // repeatCount = 3 하나만 바꿔도 등록되는 빈 개수 자체가 달라진다 - @Bean
            // 메서드 3개를 미리 나열해 둔 게 아니라, registerBeanDefinitions() 안의 for
            // 루프가 그 개수를 그때그때 결정한다.
            assertThat(messages).containsExactly("Hi-0", "Hi-1", "Hi-2");
        }
    }

    @Test
    void importBeanDefinitionRegistrarGeneratesPredictableBeanNames() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScenarioCConfig.class)) {
            assertThat(context.containsBeanDefinition("greeting0")).isTrue();
            assertThat(context.containsBeanDefinition("greeting1")).isTrue();
            assertThat(context.containsBeanDefinition("greeting2")).isTrue();
            assertThat(context.containsBeanDefinition("greeting3")).isFalse();
        }
    }

    @Test
    void importAwareInjectsTheEnablingAnnotationsAttributesAfterConstruction() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScenarioDConfig.class)) {
            GreetingSettingsHolder holder = context.getBean(GreetingSettingsHolder.class);

            // GreetingSettingsHolder는 생성자로도, @Bean 메서드 파라미터로도 "->"라는 값을
            // 받은 적이 없다 - ImportAware를 구현했기 때문에 ImportAwareBeanPostProcessor가
            // 초기화 이후 별도로 setImportMetadata()를 호출해 준 것이다.
            assertThat(holder.prefix()).isEqualTo("->");
        }
    }
}
