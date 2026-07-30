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
        return body.toString();
    }
}
