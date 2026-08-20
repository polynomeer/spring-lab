package lab.experiments.exposeproxy;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExposeProxyTest {

    @Test
    void withoutExposeProxyPlainSelfInvocationBypassesTheAdvice() {
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.addAdvice(interceptor);
        // exposeProxy 기본값은 false다.
        Greeter proxy = (Greeter) factory.getProxy();

        proxy.greetViaPlainSelfInvocation();

        assertThat(interceptor.greetCount()).isZero();
    }

    @Test
    void callingAopContextCurrentProxyWithoutExposeProxyThrows() {
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.addAdvice(new CountingInterceptor());
        Greeter proxy = (Greeter) factory.getProxy();

        assertThatThrownBy(proxy::greetViaAopContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exposeProxy");
    }

    @Test
    void withExposeProxyTrueSelfInvocationThroughAopContextGoesThroughTheAdvice() {
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.addAdvice(interceptor);
        factory.setExposeProxy(true);
        Greeter proxy = (Greeter) factory.getProxy();

        String result = proxy.greetViaAopContext();

        assertThat(result).isEqualTo("Hello");
        assertThat(interceptor.greetCount()).isEqualTo(1);
    }

    @Test
    void exposingTheProxyAloneDoesNotFixPlainThisCallsThatDontOptIn() {
        // exposeProxy=true는 "이 프록시를 스레드에 노출해 둔다"는 것뿐이다 - 대상 클래스가
        // 여전히 그냥 this.greet()를 쓴다면, 노출된 프록시를 스스로 찾아가지 않는 이상
        // 아무 효과가 없다.
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.addAdvice(interceptor);
        factory.setExposeProxy(true);
        Greeter proxy = (Greeter) factory.getProxy();

        proxy.greetViaPlainSelfInvocation();

        assertThat(interceptor.greetCount()).isZero();
    }

    @Test
    void enableAspectJAutoProxyExposeProxyMakesAopContextSelfInvocationWorkForRealDeclarativeAdvice() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ExposeProxyAspectConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);
            LoggingAspect loggingAspect = context.getBean(LoggingAspect.class);

            // @EnableAspectJAutoProxy(exposeProxy = true) 하나가 AnnotationAwareAspectJAutoProxyCreator가
            // 만드는 모든 프록시(@Transactional/@Cacheable과 같은 자동 프록시 생성 경로)에
            // 이 설정을 적용한다 - AopContext.currentProxy()를 통한 self-invocation이
            // 실제 선언적 어드바이스(@LoggedOperation)에도 정상적으로 먹힌다.
            orderService.placeOrderViaAopContextSelfInvocation();

            assertThat(loggingAspect.invocationCount()).isEqualTo(1);
        }
    }
}
