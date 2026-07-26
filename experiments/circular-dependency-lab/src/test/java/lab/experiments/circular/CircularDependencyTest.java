package lab.experiments.circular;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircularDependencyTest {

    @Test
    void constructorCircularReferenceFailsAtStartup() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ConstructorCircularA.class);
        context.registerBean(ConstructorCircularB.class);

        // 조기 노출(early exposure)은 인스턴스가 생긴 "뒤"에야 등록된다 - 생성자 인자로는
        // 아직 존재하지도 않는 인스턴스를 넘겨줄 수 없다(1주차 문서 참고).
        assertThatThrownBy(context::refresh).isInstanceOf(UnsatisfiedDependencyException.class);

        context.close();
    }

    @Test
    void setterCircularReferenceResolvesSuccessfully() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SetterCircularA.class);
        context.registerBean(SetterCircularB.class);
        context.refresh();

        SetterCircularA a = context.getBean(SetterCircularA.class);
        SetterCircularB b = context.getBean(SetterCircularB.class);

        assertThat(a.getB()).isSameAs(b);
        assertThat(b.getA()).isSameAs(a);

        context.close();
    }

    @Test
    void lazyCircularReferenceResolvesSuccessfullyEvenThroughConstructors() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(LazyCircularA.class);
        context.registerBean(LazyCircularB.class);
        context.refresh();

        LazyCircularA a = context.getBean(LazyCircularA.class);
        LazyCircularB b = context.getBean(LazyCircularB.class);

        // A -> B는 평범한 의존성이라 실제 타입 그대로 주입된다.
        assertThat(a.getB()).isSameAs(b);
        assertThat(a.getB().getClass()).isEqualTo(LazyCircularB.class);

        // B -> A만 @Lazy라 지연 프록시가 주입된다 - 실제 클래스와 다르지만, 메서드를
        // 호출하면 투명하게 진짜 A로 위임된다.
        assertThat(b.getA().getClass()).isNotEqualTo(LazyCircularA.class);
        assertThat(b.getA().getB()).isSameAs(b);

        context.close();
    }

    @Test
    void aopProxiedCircularReferenceExposesTheSameProxyEarly() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AopConfig.class);
        context.registerBean(LoggingAspect.class);
        context.registerBean(ProxiedCircularA.class);
        context.registerBean(ProxiedCircularB.class);
        context.refresh();

        ProxiedCircularA a = context.getBean(ProxiedCircularA.class);
        ProxiedCircularB b = context.getBean(ProxiedCircularB.class);

        assertThat(AopUtils.isAopProxy(a)).isTrue();
        // b가 순환 참조 해석 도중에 "조기 참조"로 받은 a가, 최종적으로 컨테이너에 등록된
        // a와 정확히 같은 프록시 객체인지 - getEarlyBeanReference(6주차)가 실제로
        // 보장하는 것이 바로 이것이다.
        assertThat(b.getA()).isSameAs(a);
        assertThat(a.getB()).isSameAs(b);
        assertThat(a.greet()).isEqualTo("a");

        context.close();
    }
}
