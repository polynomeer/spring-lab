package lab.minispring.webmvc;

import java.lang.reflect.Parameter;

import jakarta.servlet.http.HttpServletRequest;

public interface MiniArgumentResolver {

    boolean supports(Parameter parameter);

    Object resolve(Parameter parameter, HttpServletRequest request) throws Exception;
}
