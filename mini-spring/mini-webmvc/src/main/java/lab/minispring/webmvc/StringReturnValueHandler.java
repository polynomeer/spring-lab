package lab.minispring.webmvc;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

public final class StringReturnValueHandler implements MiniReturnValueHandler {

    @Override
    public boolean supports(Method method) {
        return method.getReturnType() == String.class;
    }

    @Override
    public void handle(Object returnValue, Method method, HttpServletResponse response) throws Exception {
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write((String) returnValue);
    }
}
