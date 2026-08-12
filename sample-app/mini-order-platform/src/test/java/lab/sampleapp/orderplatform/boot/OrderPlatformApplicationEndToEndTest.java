package lab.sampleapp.orderplatform.boot;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * project 32의 RequestObservationEndToEndTest와 같은 형식이다 - 다만 대상은 우리 캡스톤
 * 전체다. OrderPlatformApplication을 @EnableAutoConfiguration으로 띄우면, 이 모듈이
 * 자체적으로 만든 두 AutoConfiguration(결제/알림)은 물론 spring-extensions/mini-observability-starter의
 * RequestObservationAutoConfiguration까지 - 우리가 전혀 손대지 않은 요청 관측 인터셉터가 -
 * 실제 DispatcherServlet을 거치는 요청에 자동으로 걸린다.
 */
class OrderPlatformApplicationEndToEndTest {

    private AnnotationConfigWebApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void realHttpRequestIsRecordedByTheAutoDiscoveredObservationInterceptor() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(OrderPlatformApplication.class);
        context.refresh();

        ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
        CapturingObservationHandler handler = new CapturingObservationHandler();
        observationRegistry.observationConfig().observationHandler(handler);

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // 존재하지 않는 주문 조회 - 404가 나도 DispatcherServlet은 이미 이 요청을 처리했고,
        // 우리가 배선한 적 없는 인터셉터가 그 사실을 실제 Micrometer ObservationRegistry에
        // 기록했어야 한다.
        mockMvc.perform(get("/orders/999999")).andExpect(status().isNotFound());

        List<String> recordedUris = handler.completedObservations().stream()
                .map(observationContext -> observationContext.getLowCardinalityKeyValue("uri").getValue())
                .toList();
        assertThat(recordedUris).contains("/orders/999999");
    }
}
