package lab.minispring.webmvc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

// 실제 RequestMappingHandlerMapping의 극단적 축소판 - path variable/패턴 매칭은 없고
// (경로, HTTP 메서드) 문자열 그대로의 정확한 일치만 지원한다. PathVariable 등 패턴 매칭은
// 16주차(ArgumentResolver)로 미룬다.
public final class AnnotationHandlerMapping implements HandlerMapping {

    private final Map<String, HandlerMethod> handlers = new HashMap<>();

    public void registerController(Object controller) {
        for (Method method : controller.getClass().getMethods()) {
            MiniRequestMapping mapping = method.getAnnotation(MiniRequestMapping.class);
            if (mapping != null) {
                handlers.put(key(mapping.method(), mapping.path()), new HandlerMethod(controller, method));
            }
        }
    }

    @Override
    public HandlerMethod getHandler(HttpServletRequest request) {
        return handlers.get(key(request.getMethod(), request.getRequestURI()));
    }

    private String key(String httpMethod, String path) {
        return httpMethod.toUpperCase() + " " + path;
    }
}
