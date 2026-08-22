package lab.experiments.ppordering;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

// PriorityOrderedBpp와 판정 로직은 같지만 Ordered만 구현한다 - 이 버킷은
// PriorityOrdered 버킷 "전체"가 등록된 뒤에야 등록되기 시작한다. 즉 이 안에서
// order 값이 아무리 작아도(=우선순위가 높아도), PriorityOrdered 버킷의 order 값이
// 아무리 커도(=그 버킷 안에서는 우선순위가 낮아도) 항상 PriorityOrdered 쪽이 먼저다.
public class OrderedBpp implements BeanPostProcessor, Ordered {

    private final String label;
    private final int order;
    private final List<String> log;

    public OrderedBpp(String label, int order, List<String> log) {
        this.label = label;
        this.order = order;
        this.log = log;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Target) {
            log.add(label);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
