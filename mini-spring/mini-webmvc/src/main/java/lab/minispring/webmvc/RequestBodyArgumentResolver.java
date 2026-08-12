package lab.minispring.webmvc;

import java.io.BufferedReader;
import java.lang.reflect.Parameter;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestBodyArgumentResolver implements MiniArgumentResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return parameter.isAnnotationPresent(MiniRequestBody.class);
    }

    @Override
    public Object resolve(Parameter parameter, HttpServletRequest request) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        Class<?> targetType = parameter.getType();
        if (targetType == String.class) {
            // 실제 Spring의 StringHttpMessageConverter와 같은 지점 - 대상 타입이 String이면
            // JSON으로 해석하지 않고 본문을 그대로 넘긴다.
            return body.toString();
        }
        if (!targetType.isRecord()) {
            throw new IllegalArgumentException(
                    "@MiniRequestBody only supports String or record parameter types, got: " + targetType);
        }
        return MiniJsonReader.read(body.toString(), targetType);
    }
}
