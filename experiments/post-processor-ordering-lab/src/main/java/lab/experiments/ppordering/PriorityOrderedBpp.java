package lab.experiments.ppordering;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;

// PriorityOrdered를 구현한 BPP는 registerBeanPostProcessors()에서 별도 버킷으로
// 분리되어 다른 어떤 그룹보다 먼저 등록된다 - order 값은 이 버킷 "안에서만" 정렬에 쓰인다.
public class PriorityOrderedBpp implements BeanPostProcessor, PriorityOrdered {

    private final String label;
    private final int order;
    private final List<String> log;

    public PriorityOrderedBpp(String label, int order, List<String> log) {
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
