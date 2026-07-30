package lab.minispring.webmvc;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

// String도, MiniResponseEntity도 아닌 나머지 반환 타입을 위한 fallback - 실제 Spring에서
// RequestResponseBodyMethodProcessor가 그렇듯, 순서상 더 구체적인 핸들러들 다음에 등록되는
// "기본" 핸들러 역할이다.
public final class JsonReturnValueHandler implements MiniReturnValueHandler {

    @Override
    public boolean supports(Method method) {
        return method.getReturnType() != String.class && method.getReturnType() != MiniResponseEntity.class;
    }

    @Override
    public void handle(Object returnValue, Method method, HttpServletResponse response) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MiniJsonWriter.write(returnValue));
    }
}
