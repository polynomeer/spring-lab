package lab.minispring.aop;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// 실제 Spring의 ProxyFactory와 달리 인터페이스 기반(JDK Dynamic Proxy)만 지원한다 - CGLIB
// 상당의 서브클래스 프록시는 외부 라이브러리 없이는 만들 수 없어 이번 축소 구현의 범위 밖으로
// 뒀다(10번 절 참고).
public final class MiniProxyFactory {

    private final Object target;
    private final List<MethodInterceptor> interceptors = new ArrayList<>();

    public MiniProxyFactory(Object target) {
        this.target = target;
    }

    public void addInterceptor(MethodInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy() {
        Class<?> targetClass = target.getClass();
        return (T) Proxy.newProxyInstance(
                targetClass.getClassLoader(),
                targetClass.getInterfaces(),
                (proxy, method, args) ->
                        new ReflectiveMethodInvocation(target, method, args, List.copyOf(interceptors)).proceed());
    }
}
