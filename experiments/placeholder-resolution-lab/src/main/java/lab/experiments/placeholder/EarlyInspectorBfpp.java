package lab.experiments.placeholder;

import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

// PriorityOrdered + HIGHEST_PRECEDENCE - 48번(BeanPostProcessor 등록 순서) 문서에서
// 확인한 것과 같은 그룹/order 규칙이 BeanFactoryPostProcessor 호출에도 그대로
// 적용된다. PropertySourcesPlaceholderConfigurer는 PriorityOrdered이지만 기본
// order 값이 Ordered.LOWEST_PRECEDENCE(같은 PriorityOrdered 그룹 안에서 사실상
// 꼴찌)이므로, 이 처리기를 HIGHEST_PRECEDENCE로 등록하면 반드시 그보다 먼저 실행된다
// - 그 시점에는 아직 "${greeting}"이 해석되지 않은 원본 그대로여야 한다.
public class EarlyInspectorBfpp implements BeanFactoryPostProcessor, PriorityOrdered {

    private final List<String> log;

    public EarlyInspectorBfpp(List<String> log) {
        this.log = log;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Object raw = beanFactory.getBeanDefinition("widget").getPropertyValues()
                .getPropertyValue("label").getValue();
        log.add("early(widget.label raw)=" + raw);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
