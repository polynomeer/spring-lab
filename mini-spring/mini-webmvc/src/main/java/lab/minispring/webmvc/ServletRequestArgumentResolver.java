package lab.minispring.webmvc;

import java.lang.reflect.Parameter;

import jakarta.servlet.http.HttpServletRequest;

// 애노테이션 없이 HttpServletRequest 타입 그대로 받는 파라미터 - 15주차의 "요청 객체를
// 그대로 넘겨받는" 기능을 애노테이션 기반 해석기 목록의 일부로 재구성한 것이다.
public final class ServletRequestArgumentResolver implements MiniArgumentResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return HttpServletRequest.class.equals(parameter.getType());
    }

    @Override
    public Object resolve(Parameter parameter, HttpServletRequest request) {
        return request;
    }
}
