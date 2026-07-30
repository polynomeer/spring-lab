package lab.minispring.webmvc;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

public final class ResponseEntityReturnValueHandler implements MiniReturnValueHandler {

    @Override
    public boolean supports(Method method) {
        return method.getReturnType() == MiniResponseEntity.class;
    }

    @Override
    public void handle(Object returnValue, Method method, HttpServletResponse response) throws Exception {
        MiniResponseEntity<?> entity = (MiniResponseEntity<?>) returnValue;
        response.setStatus(entity.status());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MiniJsonWriter.write(entity.body()));
    }
}
