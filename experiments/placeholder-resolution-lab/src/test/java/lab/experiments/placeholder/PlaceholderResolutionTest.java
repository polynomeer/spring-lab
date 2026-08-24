package lab.experiments.placeholder;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderResolutionTest {

    /**
     * widget의 "label" 프로퍼티는 BeanDefinitionVisitor가 걸어다니며 mutate하는
     * MutablePropertyValues에 저장된 원시 문자열 "${greeting}"이다. 이 값은
     * PropertySourcesPlaceholderConfigurer가 BeanFactoryPostProcessor로 실행되는
     * 동안 딱 한 번, 모든 빈 정의에 대해 일괄적으로 해석된다.
     *
     * AnnotatedWidget의 "${greeting}"은 애너테이션 속성(컴파일 타임 상수)이라
     * 애초에 mutate할 대상이 없다 - PropertySourcesPlaceholderConfigurer는 이걸
     * embeddedValueResolvers 목록에 함수 하나를 등록해 두는 것으로 대신하고, 실제
     * 해석은 그 빈이 실제로 만들어질 때 AutowiredAnnotationBeanPostProcessor가
     * 그 함수를 호출하는 순간(지연) 일어난다.
     */
    @Test
    void beanDefinitionPropertyValuesAreResolvedEagerlyWhileAnnotationLiteralsStayUnresolvedUntilBeanCreation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("greeting", "hello")));

            GenericBeanDefinition widgetBd = new GenericBeanDefinition();
            widgetBd.setBeanClass(Widget.class);
            widgetBd.getPropertyValues().add("label", "${greeting}");
            context.registerBeanDefinition("widget", widgetBd);

            context.register(PlaceholderLabConfig.class, AnnotatedWidget.class);
            context.refresh();

            @SuppressWarnings("unchecked")
            List<String> log = context.getBean("log", List.class);

            assertThat(log).containsExactly(
                    // PropertySourcesPlaceholderConfigurer보다 먼저 실행되는 시점 -
                    // widget의 BeanDefinition은 아직 원본 그대로("${greeting}")다
                    "early(widget.label raw)=${greeting}",
                    // PropertySourcesPlaceholderConfigurer 이후 - BeanDefinitionVisitor가
                    // 이미 widget의 BeanDefinition 자체를 "hello"로 mutate해 뒀다
                    "late(widget.label raw)=hello",
                    // 반면 애너테이션 속성은 지금 이 시점에도 여전히 리터럴 그대로다 -
                    // 컨테이너가 아직 이 필드를 실제로 채우지 않았기 때문이다
                    "late(@Value literal)=${greeting}");

            // 두 방식 모두 최종적으로는 똑같이 해석된 값을 갖는다 - 다만 "언제,
            // 무엇을 mutate해서" 그 결과에 도달했는지가 서로 완전히 다른 경로였을 뿐이다.
            assertThat(context.getBean(Widget.class).getLabel()).isEqualTo("hello");
            assertThat(context.getBean(AnnotatedWidget.class).getLabel()).isEqualTo("hello");
        }
    }
}
