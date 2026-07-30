package lab.ext.observability.core;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestObservationInterceptorTest {

    private RequestObservationProperties propertiesWithThreshold(Duration threshold, String... excludePaths) {
        RequestObservationProperties properties = new RequestObservationProperties();
        properties.setSlowThreshold(threshold);
        properties.setExcludePaths(List.of(excludePaths));
        return properties;
    }

    @Test
    void recordsARequestFasterThanTheThresholdAsNotSlow() throws Exception {
        ObservationLog log = new ObservationLog();
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ofSeconds(10)), log);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(log.entries()).hasSize(1);
        assertThat(log.entries().get(0).path()).isEqualTo("/api/hello");
        assertThat(log.entries().get(0).slow()).isFalse();
    }

    @Test
    void recordsARequestSlowerThanTheThresholdAsSlow() throws Exception {
        ObservationLog log = new ObservationLog();
        // 임계값을 0으로 둬서, 실제로 sleep 없이도 "느린 요청" 분기를 재현한다.
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ZERO), log);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(log.entries()).hasSize(1);
        assertThat(log.entries().get(0).slow()).isTrue();
    }

    @Test
    void excludedPathIsNeverRecorded() throws Exception {
        ObservationLog log = new ObservationLog();
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ofSeconds(10), "/actuator/**"), log);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(log.entries()).isEmpty();
    }

    @Test
    void nonExcludedPathIsStillRecorded() throws Exception {
        ObservationLog log = new ObservationLog();
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ofSeconds(10), "/actuator/**"), log);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(log.entries()).hasSize(1);
    }
}
