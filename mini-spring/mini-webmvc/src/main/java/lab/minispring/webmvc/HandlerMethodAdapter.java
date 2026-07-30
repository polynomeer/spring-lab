package lab.minispring.webmvc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 실제 RequestMappingHandlerAdapter의 극단적 축소판 - 인자 해석(4단계)과 반환값 처리
// (5단계)를 이 어댑터가 소유한다(실제 Spring도 ArgumentResolver/ReturnValueHandler를
// DispatcherServlet이 아니라 RequestMappingHandlerAdapter에 등록한다).
public final class HandlerMethodAdapter implements HandlerAdapter {

    private final List<MiniArgumentResolver> argumentResolvers;
    private final List<MiniReturnValueHandler> returnValueHandlers;

    public HandlerMethodAdapter(List<MiniArgumentResolver> argumentResolvers,
            List<MiniReturnValueHandler> returnValueHandlers) {
        this.argumentResolvers = argumentResolvers;
        this.returnValueHandlers = returnValueHandlers;
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof HandlerMethod;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.method();

        Object[] args = resolveArguments(method, request);

        Object result;
        try {
            result = method.invoke(handlerMethod.bean(), args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }

        for (MiniReturnValueHandler returnValueHandler : returnValueHandlers) {
            if (returnValueHandler.supports(method)) {
                returnValueHandler.handle(result, method, response);
                return;
            }
        }
        throw new IllegalStateException("no ReturnValueHandler for method: " + method);
    }

    private Object[] resolveArguments(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            args[i] = resolveArgument(parameters[i], request);
        }
        return args;
    }

    private Object resolveArgument(Parameter parameter, HttpServletRequest request) throws Exception {
        for (MiniArgumentResolver resolver : argumentResolvers) {
            if (resolver.supports(parameter)) {
                return resolver.resolve(parameter, request);
            }
        }
        throw new IllegalStateException("no ArgumentResolver for parameter: " + parameter);
    }
}
