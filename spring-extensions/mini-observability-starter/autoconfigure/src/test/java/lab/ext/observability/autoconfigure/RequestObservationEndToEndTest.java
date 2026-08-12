package lab.ext.observability.autoconfigure;

import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @EnableAutoConfiguration 하나로 - 우리 RequestObservationAutoConfiguration은 물론
// Spring Boot의 표준 웹 자동 설정(DispatcherServletAutoConfiguration 등)까지 전부
// .imports 파일로 자동 발견된다(18주차에서 확인한 것과 같은 메커니즘). 실제
// DispatcherServlet을 거친 요청이 우리 인터셉터에 잡혀 진짜 ObservationRegistry에
// 도달하는지까지 확인하는 End-to-End 테스트다 - CapturingObservationHandler를 컨텍스트
// refresh 이후, 요청을 보내기 전에 레지스트리에 등록해 두면 충분하다(Observation.start()가
// 매번 그 시점의 핸들러 목록을 다시 조회하기 때문).
class RequestObservationEndToEndTest {

    @Test
    void realHttpRequestThroughDispatcherServletIsRecordedByTheAutoDiscoveredInterceptor() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(DemoAppConfig.class);
        context.refresh();

        ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
        CapturingObservationHandler handler = new CapturingObservationHandler();
        observationRegistry.observationConfig().observationHandler(handler);

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/hello")).andExpect(status().isOk());

        List<String> recordedUris = handler.completedObservations().stream()
                .map(observationContext -> observationContext.getLowCardinalityKeyValue("uri").getValue())
                .toList();
        assertThat(recordedUris).contains("/api/hello");

        context.close();
    }
}
