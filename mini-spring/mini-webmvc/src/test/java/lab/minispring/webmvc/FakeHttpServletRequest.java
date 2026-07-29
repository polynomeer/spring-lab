package lab.minispring.webmvc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;

// HttpServletRequest는 실제 구현체를 얻으려면 서블릿 컨테이너가 필요한 큰 인터페이스다.
// 컨테이너 없이 우리 디스패처만 테스트하기 위해, 실제로 호출하는 극소수의 메서드만 처리하고
// 나머지는 UnsupportedOperationException을 던지는 동적 프록시로 대체한다(11주차 JDK Dynamic
// Proxy 실험과 같은 메커니즘).
final class FakeHttpServletRequest implements InvocationHandler {

    private final String method;
    private final String requestURI;

    private FakeHttpServletRequest(String method, String requestURI) {
        this.method = method;
        this.requestURI = requestURI;
    }

    static HttpServletRequest create(String method, String requestURI) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                FakeHttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                new FakeHttpServletRequest(method, requestURI));
    }

    @Override
    public Object invoke(Object proxy, Method invokedMethod, Object[] args) {
        return switch (invokedMethod.getName()) {
            case "getMethod" -> method;
            case "getRequestURI" -> requestURI;
            case "toString" -> "FakeHttpServletRequest[" + method + " " + requestURI + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(invokedMethod.getName());
        };
    }
}
