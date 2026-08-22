package lab.experiments.ppordering;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.Order;

// Ordered/PriorityOrdered 어느 쪽도 구현하지 않았다 - @Order 애너테이션만 붙였다.
// registerBeanPostProcessors()의 버킷 분류는 beanFactory.isTypeMatch(ppName,
// PriorityOrdered.class)/Ordered.class, 즉 "인터페이스를 구현했는가"만 본다 -
// @Order 애너테이션 값은 이 분류에 전혀 반영되지 않으므로 이 빈은 "나머지"
// 버킷(nonOrderedPostProcessors)으로 떨어진다. 게다가 그 버킷은 sortPostProcessors()
// 호출 자체가 없어서(소스 확인), @Order 값과 무관하게 빈 정의가 등록된 순서 그대로
// 실행된다 - 이 클래스는 그 순서상 "먼저" 등록되도록 이름을 붙였을 뿐, order 값
// 자체(Integer.MIN_VALUE, 숫자로는 가장 앞선 우선순위)는 사실상 아무 의미가 없다.
@Order(Integer.MIN_VALUE)
public class PlainOrderAnnotatedFirstBpp implements BeanPostProcessor {

    static final String LABEL = "plain(@Order=MIN_VALUE, bean method 순서상 첫 번째)";

    private final List<String> log;

    public PlainOrderAnnotatedFirstBpp(List<String> log) {
        this.log = log;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Target) {
            log.add(LABEL);
        }
        return bean;
    }
}
