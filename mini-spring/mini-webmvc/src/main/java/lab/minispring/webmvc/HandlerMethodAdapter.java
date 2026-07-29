package lab.minispring.webmvc;

import java.lang.reflect.InvocationTargetException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 이번 주(3단계)는 인자 해석기(ArgumentResolver, 16주차)가 아직 없다 - 컨트롤러 메서드는
// 파라미터가 없거나, HttpServletRequest 하나만 받을 수 있다.
public final class HandlerMethodAdapter implements HandlerAdapter {

    @Override
    public boolean supports(Object handler) {
        return handler instanceof HandlerMethod;
    }

    @Override
    public Object handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        try {
            if (handlerMethod.method().getParameterCount() == 0) {
                return handlerMethod.method().invoke(handlerMethod.bean());
            }
            return handlerMethod.method().invoke(handlerMethod.bean(), request);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }
}
