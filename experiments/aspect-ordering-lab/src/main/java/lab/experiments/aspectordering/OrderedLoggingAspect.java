package lab.experiments.aspectordering;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;

// 같은 클래스를 order 값만 다르게 여러 번 빈으로 등록한다 - @Order 애너테이션은
// 클래스 하나에 값 하나만 고정할 수 있지만, Ordered 인터페이스는 인스턴스마다
// 다른 값을 런타임에 줄 수 있다(48번 문서의 PriorityOrderedBpp/OrderedBpp와 같은 패턴).
// AnnotationAwareOrderComparator는 @Order 애너테이션뿐 아니라 Ordered 구현도
// 동등하게 인식한다.
@Aspect
public class OrderedLoggingAspect implements Ordered {

    private final String label;
    private final int order;
    private final List<String> log;

    public OrderedLoggingAspect(String label, int order, List<String> log) {
        this.label = label;
        this.order = order;
        this.log = log;
    }

    @Around("execution(* lab.experiments.aspectordering.Greeter.greet(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        log.add(label + "-before");
        try {
            return joinPoint.proceed();
        } finally {
            log.add(label + "-after");
        }
    }

    @Override
    public int getOrder() {
        return order;
    }
}
