package lab.minispring.webmvc;

import java.lang.reflect.Method;

public record HandlerMethod(Object bean, Method method) {
}
