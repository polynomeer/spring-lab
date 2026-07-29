package lab.experiments.mvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class TraceInterceptor implements HandlerInterceptor {

    private static final List<String> events = new CopyOnWriteArrayList<>();

    public static List<String> events() {
        return List.copyOf(events);
    }

    public static void reset() {
        events.clear();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        events.add("preHandle");
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) {
        events.add("postHandle");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        events.add("afterCompletion");
    }
}
