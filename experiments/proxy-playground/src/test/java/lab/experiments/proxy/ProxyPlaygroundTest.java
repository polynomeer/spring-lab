package lab.experiments.proxy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyPlaygroundTest {

    @Test
    void interfaceBackedTargetProducesJdkProxyByDefault() {
        GreetableImpl target = new GreetableImpl();
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(interceptor);

        Greetable proxy = (Greetable) pf.getProxy();

        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isTrue();
        assertThat(AopUtils.isCglibProxy(proxy)).isFalse();
        assertThat(proxy.getClass()).isNotEqualTo(GreetableImpl.class);
        assertThat(AopUtils.getTargetClass(proxy)).isEqualTo(GreetableImpl.class);
        assertThat(proxy.greet("A")).isEqualTo("Hello, A");
        assertThat(interceptor.getInvokedMethodNames()).containsExactly("greet");
    }

    @Test
    void noInterfaceTargetForcesCglibProxy() {
        // ProxyFactory는 대상 클래스가 구현한 인터페이스가 하나도 없으면 JDK Dynamic Proxy를
        // 만들 수 없으므로 자동으로 CGLIB로 넘어간다 - proxyTargetClass를 켜지 않아도 그렇다.
        PlainGreeter target = new PlainGreeter();
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(new CountingInterceptor());

        Object proxy = pf.getProxy();

        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isFalse();
        assertThat(proxy.getClass()).isNotEqualTo(PlainGreeter.class);
        assertThat(PlainGreeter.class.isAssignableFrom(proxy.getClass())).isTrue();
        assertThat(((PlainGreeter) proxy).greet("B")).isEqualTo("Hi, B");
    }

    @Test
    void proxyTargetClassTrueForcesCglibEvenWithInterface() {
        GreetableImpl target = new GreetableImpl();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(new CountingInterceptor());

        Object proxy = pf.getProxy();

        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(proxy).isInstanceOf(GreetableImpl.class);
    }

    @Test
    void finalClassCannotBeCglibProxied() {
        ProxyFactory pf = new ProxyFactory(new FinalGreeter());

        assertThatThrownBy(pf::getProxy).isInstanceOf(AopConfigException.class);
    }

    @Test
    void finalAndPrivateMethodsAreNeverIntercepted() {
        MixedVisibilityGreeter target = new MixedVisibilityGreeter();
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(interceptor);

        MixedVisibilityGreeter proxy = (MixedVisibilityGreeter) pf.getProxy();

        // final 메서드: CGLIB가 서브클래스에서 오버라이드할 수 없어 상속받은 원본이 그대로
        // 호출된다 - 결과값은 맞지만 인터셉터를 전혀 거치지 않는다.
        assertThat(proxy.greetFinally("C")).isEqualTo("Final hey, C");

        // public 메서드: 프록시를 거쳐 정상적으로 인터셉트된다.
        assertThat(proxy.greet("C")).isEqualTo("Hey, C");

        // private 메서드(greetPrivately)는 greet() 내부에서 this로 직접 호출되므로 애초에
        // 오버라이딩 대상이 아니고, 인터셉터 기록에도 남지 않는다.
        assertThat(interceptor.getInvokedMethodNames())
                .containsExactly("greet")
                .doesNotContain("greetFinally", "greetPrivately");
    }

    @Test
    void equalsAndHashCodeBypassTheAdviceChainWhenTargetDoesNotOverrideThem() {
        // GreetableImpl은 equals()/hashCode()를 직접 정의하지 않는다 - JdkDynamicAopProxy#invoke는
        // 이 경우 인터셉터 체인을 타지 않고 프록시 자신의 equals()/hashCode()로 바로 처리한다
        // (JdkDynamicAopProxy 소스의 cache.equalsDefined/hashCodeDefined 분기).
        GreetableImpl target = new GreetableImpl();
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(interceptor);
        Greetable proxy = (Greetable) pf.getProxy();

        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.hashCode()).isNotNull();

        assertThat(interceptor.getInvokedMethodNames()).isEmpty();
    }

    @Test
    void castingBehaviorDiffersBetweenJdkAndCglibProxies() {
        GreetableImpl target = new GreetableImpl();

        Object jdkProxy = new ProxyFactory(target).getProxy();
        assertThatThrownBy(() -> {
            GreetableImpl casted = (GreetableImpl) jdkProxy;
        }).isInstanceOf(ClassCastException.class);

        ProxyFactory cglibFactory = new ProxyFactory(target);
        cglibFactory.setProxyTargetClass(true);
        Object cglibProxy = cglibFactory.getProxy();
        GreetableImpl casted = (GreetableImpl) cglibProxy;
        assertThat(casted).isNotNull();
    }

    @Test
    void proxyInstanceDoesNotShareFieldsWithTheWrappedTarget() throws Exception {
        // CGLIB 프록시는 Objenesis로 생성자를 건너뛰고 만들어진 "별개의" 서브클래스
        // 인스턴스다 - greet() 호출은 인터셉터 체인을 거쳐 결국 target 객체의 메서드를
        // 리플렉션으로 호출하므로 target의 필드는 채워지지만, 프록시 인스턴스 자신이 상속받은
        // 필드는 계속 비어 있다.
        GreetableImpl target = new GreetableImpl();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        Greetable proxy = (Greetable) pf.getProxy();

        proxy.greet("D");

        assertThat(target.getLastGreeted()).isEqualTo("D");

        Field field = GreetableImpl.class.getDeclaredField("lastGreeted");
        field.setAccessible(true);
        assertThat(field.get(proxy)).isNull();
    }

    @Test
    void selfInvocationBypassesTheProxyEntirely() {
        SelfInvokingGreeter target = new SelfInvokingGreeter();
        CountingInterceptor interceptor = new CountingInterceptor();
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(interceptor);
        Greetable proxy = (Greetable) pf.getProxy();

        assertThat(proxy.greet("E")).isEqualTo("Outer: Inner: E");
        // greet() 내부의 greetInner() 호출은 프록시가 아니라 target의 this를 통해 일어나므로
        // 인터셉터에는 "greet"만 기록되고 "greetInner"는 기록되지 않는다.
        assertThat(interceptor.getInvokedMethodNames()).containsExactly("greet");

        // 반대로 외부에서 프록시를 거쳐 직접 부르면 정상적으로 인터셉트된다.
        ProxyFactory pf2 = new ProxyFactory(target);
        pf2.setProxyTargetClass(true);
        pf2.addAdvice(interceptor);
        SelfInvokingGreeter greetInnerProxy = (SelfInvokingGreeter) pf2.getProxy();
        greetInnerProxy.greetInner("F");
        assertThat(interceptor.getInvokedMethodNames()).containsExactly("greet", "greetInner");
    }
}
