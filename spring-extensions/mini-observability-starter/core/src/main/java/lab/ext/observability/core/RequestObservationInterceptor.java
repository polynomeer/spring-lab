package lab.ext.observability.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequestObservationInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = RequestObservationInterceptor.class.getName() + ".startTime";

    private final RequestObservationProperties properties;
    private final ObservationLog observationLog;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestObservationInterceptor(RequestObservationProperties properties, ObservationLog observationLog) {
        this.properties = properties;
        this.observationLog = observationLog;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isExcluded(request)) {
            return true;
        }
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime == null) {
            return;
        }
        long elapsedNanos = System.nanoTime() - (long) startTime;
        long elapsedMillis = elapsedNanos / 1_000_000;
        boolean slow = elapsedMillis >= properties.getSlowThreshold().toMillis();
        observationLog.record(new ObservationEntry(request.getRequestURI(), elapsedMillis, slow));
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
