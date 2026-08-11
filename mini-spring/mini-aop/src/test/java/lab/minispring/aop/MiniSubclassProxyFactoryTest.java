package lab.minispring.aop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 7번 절("남겨 둔 질문")이 미뤄 뒀던 CGLIB 상당 서브클래스 프록시를 실제로 구현해 검증한다.
 * {@link MiniProxyFactory}(JDK Dynamic Proxy)가 이미 검증해 둔 인터셉터 체인 로직
 * ({@link ReflectiveMethodInvocation})은 그대로 재사용하므로, 여기서는 "인터페이스 없는
 * 대상을 프록시할 수 있다"는 것 자체와 그로 인해 생기는 실제 CGLIB 프록시 특유의 경계
 * 조건만 다룬다.
 */
class MiniSubclassProxyFactoryTest {

    @Test
    void interceptsOverridableMethodsAndDelegatesToTheSeparateTargetInstance() {
        StatefulService target = new StatefulService();
        target.setLabel("real-target");
        CallCountInterceptor counter = new CallCountInterceptor();
        MiniSubclassProxyFactory factory = new MiniSubclassProxyFactory(target);
        factory.addInterceptor(counter);

        StatefulService proxy = factory.getProxy();

        assertThat(proxy.greet()).isEqualTo("hello, real-target"); // target의 실제 상태를 봄
        assertThat(counter.getCount("greet")).isEqualTo(1);
    }

    @Test
    void finalMethodsAreNotInterceptedAndSeeTheProxysOwnStateNotTheTargets() {
        StatefulService target = new StatefulService();
        target.setLabel("real-target");
        CallCountInterceptor counter = new CallCountInterceptor();
        MiniSubclassProxyFactory factory = new MiniSubclassProxyFactory(target);
        factory.addInterceptor(counter);

        StatefulService proxy = factory.getProxy();

        // final 메서드는 오버라이드(따라서 가로채기) 자체가 불가능하다 - 인터셉터를 전혀
        // 거치지 않고, 프록시 인스턴스 자신의 상속된 구현이 그대로 실행된다. 그 구현이
        // 읽는 label 필드는 target의 것이 아니라 프록시 자신의(생성자가 실행되며 초기화된)
        // "default" 값이다 - target.setLabel("real-target")은 여기 전혀 영향을 못 준다.
        assertThat(proxy.finalGreet()).isEqualTo("final hello, default");
        assertThat(counter.getCount("finalGreet")).isEqualTo(0);

        // 대조군: target 자신에게 직접 물으면 당연히 실제 상태를 본다.
        assertThat(target.finalGreet()).isEqualTo("final hello, real-target");
    }

    @Test
    void theProxyIsAnInstanceOfTheTargetClassUnlikeAJdkDynamicProxy() {
        // MiniProxyFactory(JDK Dynamic Proxy)는 인터페이스가 없으면 애초에 프록시를 만들
        // 수조차 없다 - 이게 이 클래스가 존재하는 이유다. 여기서 만든 프록시는 실제
        // StatefulService의 서브클래스이므로 instanceof가 그대로 성립한다.
        StatefulService target = new StatefulService();
        MiniSubclassProxyFactory factory = new MiniSubclassProxyFactory(target);

        StatefulService proxy = factory.getProxy();

        assertThat(proxy).isInstanceOf(StatefulService.class);
        assertThat(proxy).isNotSameAs(target); // 그러나 target과 같은 객체는 아니다
    }

    @Test
    void proxyingAFinalClassIsImpossible() {
        MiniSubclassProxyFactory factory = new MiniSubclassProxyFactory(new FinalService());

        assertThatThrownBy(factory::getProxy).isInstanceOf(RuntimeException.class);
    }
}
