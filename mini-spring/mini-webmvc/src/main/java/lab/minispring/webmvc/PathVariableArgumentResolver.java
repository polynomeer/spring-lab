package lab.minispring.webmvc;

import java.lang.reflect.Parameter;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

// AnnotationHandlerMapping이 매칭 시점에 request 속성으로 남겨 둔 경로 변수 맵을 읽는다
// (실제 Spring의 HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE와 같은 방식 - 매핑 단계와
// 인자 해석 단계가 request 속성이라는 채널로 통신한다).
public final class PathVariableArgumentResolver implements MiniArgumentResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(MiniPathVariable.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object resolve(Parameter parameter, HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.PATH_VARIABLES_ATTRIBUTE);
        Map<String, String> variables = attribute == null ? Map.of() : (Map<String, String>) attribute;
        String name = parameter.getAnnotation(MiniPathVariable.class).value();
        String rawValue = variables.get(name);
        return TypeConversion.convert(rawValue, parameter.getType());
    }
}
