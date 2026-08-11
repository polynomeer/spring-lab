package lab.minispring.aop;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.List;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * {@link MiniProxyFactory}(JDK Dynamic Proxy)와 같은 {@link MethodInterceptor}/
 * {@link MethodInvocation} 체인을 그대로 재사용하되, 인터페이스가 없는 대상도 프록시할 수
 * 있게 실제 서브클래스를 런타임에 만든다 - CGLIB이 하는 일과 같은 접근(바이트코드로 대상
 * 클래스의 서브클래스를 생성)이다. ASM을 직접 다루는 대신 ByteBuddy 라이브러리를 그대로
 * 가져다 썼다 - 실제 Spring도 CGLIB 자체를 재구현하지 않고 벤더링(`org.springframework.cglib.*`)해서
 * 쓴다는 점에서 같은 선택이다.
 *
 * <p>{@link MiniProxyFactory}와 달리 프록시 인스턴스 자신이 target과는 별개의, 새로
 * 생성된 서브클래스 객체다({@code target.getClass()}를 상속만 할 뿐 target의 필드 값을
 * 복사하지 않는다) - {@code final}로 선언돼 오버라이드(따라서 가로채기)할 수 없는
 * 메서드를 프록시를 통해 호출하면, target이 아니라 이 "빈 껍데기" 프록시 인스턴스
 * 자신의(생성자가 그대로 실행돼 초기화된) 상태를 본다. 실제 Spring의
 * {@code ObjenesisCglibAopProxy}는 Objenesis로 생성자 자체를 건너뛰어 프록시 인스턴스의
 * 필드가 전부 비어(null/0) 있지만, 이 축소 구현은 Objenesis 없이 프록시의 실제 무인자
 * 생성자를 그대로 호출한다 - 그래서 여기서는 "비어 있음"이 아니라 "target과는 다른, 프록시
 * 자신만의 초기 상태"로 나타난다. 어느 쪽이든 결론은 같다: final 메서드는 프록시를 거치지
 * 않고 target도 보지 않는다.
 */
public final class MiniSubclassProxyFactory {

    private final Object target;
    private final List<MethodInterceptor> interceptors = new ArrayList<>();

    public MiniSubclassProxyFactory(Object target) {
        this.target = target;
    }

    public void addInterceptor(MethodInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy() {
        Class<?> targetClass = target.getClass();

        InvocationHandler handler = (proxyInstance, method, args) ->
                new ReflectiveMethodInvocation(target, method, args, List.copyOf(interceptors)).proceed();

        Class<?> proxyClass = new ByteBuddy()
                .subclass(targetClass)
                .method(ElementMatchers.isPublic()
                        .and(ElementMatchers.not(ElementMatchers.isFinal()))
                        .and(ElementMatchers.not(ElementMatchers.isStatic())))
                .intercept(InvocationHandlerAdapter.of(handler))
                .make()
                .load(targetClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        try {
            return (T) proxyClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "failed to instantiate subclass proxy for " + targetClass
                            + " - MiniSubclassProxyFactory(target 클래스에 public 무인자 생성자가 필요하다)",
                    e);
        }
    }
}
