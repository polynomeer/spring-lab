package lab.minispring.webmvc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

// 실제 ExceptionHandlerExceptionResolver의 축소판 - 예외를 던진 핸들러와 같은 컨트롤러
// 빈에서 먼저 @MiniExceptionHandler를 찾고, 없으면 registerControllerAdvice()로 등록해 둔
// 전역 빈들을(등록 순서대로) 검사한다 - 실제 Spring의 "로컬 우선 → @ControllerAdvice 폴백"
// 2단계 탐색과 같은 순서다. 컴포넌트 스캔으로 advice 빈을 자동 발견하는 기능은 범위 밖이다 -
// AnnotationHandlerMapping#registerController()가 컨트롤러 빈을 명시적으로 등록받는 것과
// 똑같이, advice 빈도 명시적으로 등록받는다.
public final class AnnotationExceptionResolver implements MiniHandlerExceptionResolver {

    private final List<MiniReturnValueHandler> returnValueHandlers;
    private final List<Object> adviceBeans = new ArrayList<>();

    public AnnotationExceptionResolver(List<MiniReturnValueHandler> returnValueHandlers) {
        this.returnValueHandlers = returnValueHandlers;
    }

    public void registerControllerAdvice(Object adviceBean) {
        adviceBeans.add(adviceBean);
    }

    @Override
    public boolean resolveException(Object handler, Throwable ex, HttpServletResponse response) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }

        if (tryHandleWith(handlerMethod.bean(), ex, response)) {
            return true;
        }
        for (Object adviceBean : adviceBeans) {
            if (tryHandleWith(adviceBean, ex, response)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleWith(Object bean, Throwable ex, HttpServletResponse response) throws Exception {
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
