package lab.experiments.mvc;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

// preHandle이 false를 반환하면 HandlerAdapter/Controller까지 아예 도달하지 않는다는 것을
// 보여주기 위한 인터셉터 - "/users/blocked" 경로에만 등록한다(MvcTraceConfig 참고).
public class BlockingInterceptor implements HandlerInterceptor {

    static final AtomicBoolean controllerReached = new AtomicBoolean(false);

    static void reset() {
        controllerReached.set(false);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return false;
    }
}
