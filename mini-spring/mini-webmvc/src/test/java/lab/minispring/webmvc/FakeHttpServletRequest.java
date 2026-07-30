package lab.minispring.webmvc;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

// HttpServletRequest는 실제 구현체를 얻으려면 서블릿 컨테이너가 필요한 큰 인터페이스다.
// 컨테이너 없이 우리 디스패처만 테스트하기 위해, 실제로 호출하는 극소수의 메서드만 처리하고
// 나머지는 UnsupportedOperationException을 던지는 동적 프록시로 대체한다(11주차 JDK Dynamic
// Proxy 실험과 같은 메커니즘). setAttribute/getAttribute는 AnnotationHandlerMapping이 남긴
// 경로 변수를 PathVariableArgumentResolver가 다시 읽을 수 있어야 해서 실제로 상태를 갖는다.
final class FakeHttpServletRequest implements InvocationHandler {

    private final String method;
    private final String requestURI;
    private final Map<String, String> parameters;
    private final String body;
    private final Map<String, Object> attributes = new HashMap<>();

    private FakeHttpServletRequest(String method, String requestURI, Map<String, String> parameters, String body) {
        this.method = method;
        this.requestURI = requestURI;
        this.parameters = parameters;
        this.body = body;
    }

    static HttpServletRequest create(String method, String requestURI) {
        return build(method, requestURI, Map.of(), "");
    }

    static HttpServletRequest create(String method, String requestURI, Map<String, String> parameters) {
        return build(method, requestURI, parameters, "");
    }

    static HttpServletRequest createWithBody(String method, String requestURI, String body) {
        return build(method, requestURI, Map.of(), body);
    }

    private static HttpServletRequest build(String method, String requestURI, Map<String, String> parameters,
            String body) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                FakeHttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                new FakeHttpServletRequest(method, requestURI, parameters, body));
    }

    @Override
    public Object invoke(Object proxy, Method invokedMethod, Object[] args) {
        return switch (invokedMethod.getName()) {
            case "getMethod" -> method;
            case "getRequestURI" -> requestURI;
            case "getParameter" -> parameters.get((String) args[0]);
            case "getAttribute" -> attributes.get((String) args[0]);
            case "setAttribute" -> {
                attributes.put((String) args[0], args[1]);
                yield null;
            }
            case "getReader" -> new BufferedReader(new StringReader(body));
            case "toString" -> "FakeHttpServletRequest[" + method + " " + requestURI + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(invokedMethod.getName());
        };
    }
}
