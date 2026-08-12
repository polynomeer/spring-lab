package lab.ext.observability.core;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

// 실제 Micrometer의 Observation API를 그대로 쓴다 - preHandle에서 Observation을 만들어
// start()하고, afterCompletion에서 outcome 태그를 붙인 뒤 stop()한다. 실제 관찰 결과가
// 어디로 가는지(로그, 메트릭, 트레이싱)는 ObservationRegistry에 등록된 ObservationHandler가
// 결정한다 - 이 인터셉터는 그 핸들러가 무엇인지 전혀 몰라도 된다.
public class RequestObservationInterceptor implements HandlerInterceptor {

    public static final String OBSERVATION_NAME = "http.server.requests";

    private static final String OBSERVATION_ATTRIBUTE = RequestObservationInterceptor.class.getName() + ".observation";
    private static final String START_TIME_ATTRIBUTE = RequestObservationInterceptor.class.getName() + ".startTime";

    private final RequestObservationProperties properties;
    private final ObservationRegistry observationRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestObservationInterceptor(RequestObservationProperties properties,
            ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isExcluded(request)) {
            return true;
        }
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .contextualName(request.getMethod() + " " + request.getRequestURI())
                .lowCardinalityKeyValue("uri", request.getRequestURI())
                .start();
        request.setAttribute(OBSERVATION_ATTRIBUTE, observation);
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Object attribute = request.getAttribute(OBSERVATION_ATTRIBUTE);
        if (!(attribute instanceof Observation observation)) {
            return;
        }
        long elapsedNanos = System.nanoTime() - (long) request.getAttribute(START_TIME_ATTRIBUTE);
        boolean slow = (elapsedNanos / 1_000_000) >= properties.getSlowThreshold().toMillis();
        observation.lowCardinalityKeyValue("outcome", slow ? "SLOW" : "FAST");
        if (ex != null) {
            observation.error(ex);
        }
        observation.stop();
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
