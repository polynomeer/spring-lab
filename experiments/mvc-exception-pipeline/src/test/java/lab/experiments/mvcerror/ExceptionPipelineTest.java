package lab.experiments.mvcerror;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExceptionPipelineTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ExceptionPipelineConfig.class);
        context.refresh();

        // Spring 6.1부터 throwExceptionIfNoHandlerFound의 기본값이 true로 바뀌었다 - 예전에는
        // 명시적으로 켜야만 NoHandlerFoundException이 예외 리졸버 체인을 탔지만(setter가 이제
        // @Deprecated(forRemoval=true)인 이유), 지금은 아무 설정 없이도 그렇게 동작한다(9번 절).
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void controllerLocalExceptionHandlerTakesPriorityOverControllerAdvice() throws Exception {
        mockMvc.perform(get("/widgets/boom-local"))
                .andExpect(status().isIAmATeapot())
                .andExpect(content().string("handled-locally:local failure"));
    }

    @Test
    void controllerAdviceHandlesExceptionsWithNoLocalHandler() throws Exception {
        mockMvc.perform(get("/widgets/boom-advice-only"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("handled-by-advice:advice failure"));
    }

    @Test
    void higherPriorityControllerAdviceWinsWhenBothMatchTheSameException() throws Exception {
        mockMvc.perform(get("/widgets/boom-shared"))
                .andExpect(status().isConflict())
                .andExpect(content().string("handled-by-high-priority-advice"));
    }

    @Test
    void responseStatusExceptionSetsStatusWithoutAnyExceptionHandler() throws Exception {
        mockMvc.perform(get("/widgets/boom-response-status-exception"))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void responseStatusAnnotatedExceptionSetsStatusWithoutAnyExceptionHandler() throws Exception {
        mockMvc.perform(get("/widgets/boom-annotated-exception"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pathVariableTypeMismatchResultsInBadRequest() throws Exception {
        mockMvc.perform(get("/widgets/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyResultsInBadRequest() throws Exception {
        mockMvc.perform(post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void beanValidationFailureResultsInBadRequest() throws Exception {
        mockMvc.perform(post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noHandlerFoundResultsInNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedHttpMethodResultsInMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/widgets/1"))
                .andExpect(status().isMethodNotAllowed());
    }
}
