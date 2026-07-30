package lab.ext.observability.autoconfigure;

import java.util.List;

import lab.ext.observability.core.ObservationEntry;
import lab.ext.observability.core.ObservationLog;

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
// DispatcherServlet을 거친 요청이 우리 인터셉터에 잡혀 ObservationLog에 남는지까지
// 확인하는 End-to-End 테스트다.
class RequestObservationEndToEndTest {

    @Test
    void realHttpRequestThroughDispatcherServletIsRecordedByTheAutoDiscoveredInterceptor() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(DemoAppConfig.class);
        context.refresh();

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/hello")).andExpect(status().isOk());

        ObservationLog log = context.getBean(ObservationLog.class);
        List<String> recordedPaths = log.entries().stream().map(ObservationEntry::path).toList();
        assertThat(recordedPaths).contains("/api/hello");

        context.close();
    }
}
