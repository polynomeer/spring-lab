package lab.minispring.webmvc;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

// 실제 ExceptionHandlerExceptionResolver의 축소판 - 예외를 던진 핸들러와 같은 컨트롤러
// 빈에서 @MiniExceptionHandler(그 예외 타입)이 붙은 메서드를 찾아 대신 호출한다.
// @ControllerAdvice처럼 다른 컨트롤러의 예외까지 광범위하게 처리하는 기능은 범위 밖으로
// 뒀다 - 같은 빈 안에서만 찾는다(11번 절 참고).
public final class AnnotationExceptionResolver implements MiniHandlerExceptionResolver {

    private final List<MiniReturnValueHandler> returnValueHandlers;

    public AnnotationExceptionResolver(List<MiniReturnValueHandler> returnValueHandlers) {
        this.returnValueHandlers = returnValueHandlers;
    }

    @Override
    public boolean resolveException(Object handler, Throwable ex, HttpServletResponse response) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }

        Object bean = handlerMethod.bean();
        for (Method candidate : bean.getClass().getMethods()) {
            MiniExceptionHandler annotation = candidate.getAnnotation(MiniExceptionHandler.class);
            if (annotation != null && annotation.value().isInstance(ex)) {
                Object result = candidate.invoke(bean, ex);
                writeReturnValue(result, candidate, response);
                return true;
            }
        }
        return false;
    }

    private void writeReturnValue(Object result, Method method, HttpServletResponse response) throws Exception {
        for (MiniReturnValueHandler handler : returnValueHandlers) {
            if (handler.supports(method)) {
                handler.handle(result, method, response);
                return;
            }
        }
        throw new IllegalStateException("no ReturnValueHandler for exception handler method: " + method);
    }
}
