package lab.experiments.placeholder;

import java.lang.reflect.Field;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

// Ordered/PriorityOrdered 어느 쪽도 구현하지 않았다 - 48번 문서에서 확인한 대로,
// 이런 "나머지" 그룹의 BeanFactoryPostProcessor는 PriorityOrdered/Ordered 그룹
// 전체가 끝난 뒤에야 실행되므로, PropertySourcesPlaceholderConfigurer(PriorityOrdered)
// 보다는 무조건 나중이다.
public class LateInspectorBfpp implements BeanFactoryPostProcessor {

    private final List<String> log;

    public LateInspectorBfpp(List<String> log) {
        this.log = log;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // BeanDefinitionVisitor가 이미 이 BeanDefinition 자체를 mutate 해 뒀어야 한다.
        Object raw = beanFactory.getBeanDefinition("widget").getPropertyValues()
                .getPropertyValue("label").getValue();
        log.add("late(widget.label raw)=" + raw);

        // 반면 @Value의 값은 애너테이션 속성(컴파일 타임 상수)이라 애초에 "mutate"할
        // 대상 자체가 없다 - 지금 이 시점에도 여전히 리터럴 "${greeting}" 그대로다.
        try {
            Field field = AnnotatedWidget.class.getDeclaredField("label");
            Value valueAnnotation = field.getAnnotation(Value.class);
            log.add("late(@Value literal)=" + valueAnnotation.value());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
