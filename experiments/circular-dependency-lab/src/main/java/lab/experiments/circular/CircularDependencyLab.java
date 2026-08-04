package lab.experiments.circular;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Runnable driver for a tools/jdi-tracer / dashboard session against the 3-level
 * cache + AOP proxy pre-decision path. See
 * tools/jdi-tracer/specs/circular-dependency-lab.txt for the breakpoint spec and
 * docs/10-primary-qualifier-circular/primary-qualifier-circular.md for the
 * background analysis (조기 참조 == 최종 프록시임을 getEarlyBeanReference가 보장하는 지점).
 */
public class CircularDependencyLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AopConfig.class);
        context.registerBean(LoggingAspect.class);
        context.registerBean(ProxiedCircularA.class);
        context.registerBean(ProxiedCircularB.class);
        context.refresh();

        ProxiedCircularA a = context.getBean(ProxiedCircularA.class);
        ProxiedCircularB b = context.getBean(ProxiedCircularB.class);

        System.out.println("a isAopProxy? " + AopUtils.isAopProxy(a));
        System.out.println("b.getA() == a (early ref == final)? " + (b.getA() == a));
        System.out.println("a.greet() = " + a.greet());

        context.close();
    }
}
