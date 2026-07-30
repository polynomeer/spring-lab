package lab.minispring.webmvc;

import java.lang.reflect.Parameter;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestParamArgumentResolver implements MiniArgumentResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(MiniRequestParam.class);
    }

    @Override
    public Object resolve(Parameter parameter, HttpServletRequest request) {
        MiniRequestParam annotation = parameter.getAnnotation(MiniRequestParam.class);
        String rawValue = request.getParameter(annotation.value());
        if (rawValue == null && annotation.required()) {
            throw new IllegalArgumentException("missing required request parameter: " + annotation.value());
        }
        return TypeConversion.convert(rawValue, parameter.getType());
    }
}
