package lab.ext.observability.core;

import java.time.Duration;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

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

    private ObservationRegistry registryWith(CapturingObservationHandler handler) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
        return registry;
    }

    @Test
    void recordsARequestFasterThanTheThresholdAsNotSlow() throws Exception {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ofSeconds(10)),
                        registryWith(handler));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(handler.completedObservations()).hasSize(1);
        Observation.Context context = handler.completedObservations().get(0);
        assertThat(context.getName()).isEqualTo(RequestObservationInterceptor.OBSERVATION_NAME);
        assertThat(context.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/hello");
        assertThat(context.getLowCardinalityKeyValue("outcome").getValue()).isEqualTo("FAST");
    }

    @Test
    void recordsARequestSlowerThanTheThresholdAsSlow() throws Exception {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        // 임계값을 0으로 둬서, 실제로 sleep 없이도 "느린 요청" 분기를 재현한다.
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ZERO), registryWith(handler));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(handler.completedObservations()).hasSize(1);
        assertThat(handler.completedObservations().get(0).getLowCardinalityKeyValue("outcome").getValue())
                .isEqualTo("SLOW");
    }

    @Test
    void excludedPathIsNeverRecorded() throws Exception {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        RequestObservationInterceptor interceptor = new RequestObservationInterceptor(
                propertiesWithThreshold(Duration.ofSeconds(10), "/actuator/**"), registryWith(handler));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(handler.completedObservations()).isEmpty();
    }

    @Test
    void nonExcludedPathIsStillRecorded() throws Exception {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        RequestObservationInterceptor interceptor = new RequestObservationInterceptor(
                propertiesWithThreshold(Duration.ofSeconds(10), "/actuator/**"), registryWith(handler));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(handler.completedObservations()).hasSize(1);
    }

    @Test
    void aFailedRequestIsMarkedWithTheThrownException() throws Exception {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        RequestObservationInterceptor interceptor =
                new RequestObservationInterceptor(propertiesWithThreshold(Duration.ofSeconds(10)),
                        registryWith(handler));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException thrown = new IllegalStateException("handler exploded");

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), thrown);

        assertThat(handler.completedObservations()).hasSize(1);
        assertThat(handler.completedObservations().get(0).getError()).isSameAs(thrown);
    }
}
