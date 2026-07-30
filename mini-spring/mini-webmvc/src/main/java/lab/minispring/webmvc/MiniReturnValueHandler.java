package lab.minispring.webmvc;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

public interface MiniReturnValueHandler {

    boolean supports(Method method);

    void handle(Object returnValue, Method method, HttpServletResponse response) throws Exception;
}
