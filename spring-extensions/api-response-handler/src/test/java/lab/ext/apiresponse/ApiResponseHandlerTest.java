package lab.ext.apiresponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiResponseHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ApiResponseConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnValueHandlerWrapsTheResponseExactlyOnce() throws Exception {
        mockMvc.perform(get("/api/via-return-value-handler"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Ada"))
                .andExpect(jsonPath("$.timestamp").exists())
                // 이중 래핑됐다면 $.data.data가 생겼을 것이다.
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }

    @Test
    void responseBodyAdviceWrapsTheResponseExactlyOnce() throws Exception {
        mockMvc.perform(get("/api/via-response-body-advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("Grace"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }
}
