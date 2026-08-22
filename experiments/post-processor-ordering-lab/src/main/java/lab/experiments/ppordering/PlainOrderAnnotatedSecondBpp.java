package lab.experiments.ppordering;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.Order;

// PlainOrderAnnotatedFirstBpp와 정반대 극단의 @Order 값(Integer.MAX_VALUE, 숫자로는
// 가장 낮은 우선순위)을 붙였지만, 마찬가지로 인터페이스를 구현하지 않았으므로
// 여전히 "나머지" 버킷이다. bean method 선언 순서상 이 클래스가 나중에 등록되도록
// 이름을 붙였는데, 만약 @Order 값이 버킷 내부에서라도 의미가 있었다면 이 빈이 항상
// 맨 마지막에 실행돼야 한다 - 실제로 그런지, 아니면 등록 순서만 그대로 따르는지를
// 테스트로 확인한다.
@Order(Integer.MAX_VALUE)
public class PlainOrderAnnotatedSecondBpp implements BeanPostProcessor {

    static final String LABEL = "plain(@Order=MAX_VALUE, bean method 순서상 두 번째)";

    private final List<String> log;

    public PlainOrderAnnotatedSecondBpp(List<String> log) {
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
