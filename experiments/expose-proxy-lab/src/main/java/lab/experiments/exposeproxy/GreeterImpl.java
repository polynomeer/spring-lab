package lab.experiments.exposeproxy;

import org.springframework.aop.framework.AopContext;

public class GreeterImpl implements Greeter {

    @Override
    public String greet() {
        return "Hello";
    }

    @Override
    public String greetViaPlainSelfInvocation() {
        // 11·12·25·26·29번과 같은 패턴 - this는 프록시가 아니라 대상 객체 자신이므로
        // 어드바이스를 우회한다.
        return this.greet();
    }

    @Override
    public String greetViaAopContext() {
        // exposeProxy가 켜져 있으면, 이 스레드에 노출된 "현재 프록시"를 직접 가져와서
        // 그걸 통해 호출한다 - this가 아니라 프록시를 명시적으로 다시 거치므로 어드바이스가
        // 정상적으로 적용된다. 대가는 이 클래스가 Spring AOP API(AopContext)에 직접
        // 결합된다는 것이다.
        return ((Greeter) AopContext.currentProxy()).greet();
    }
}
