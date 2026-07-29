package lab.experiments.mvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// MockMvc의 webAppContextSetup은 진짜 네트워크 소켓 없이도 실제 DispatcherServlet 인스턴스를
// MockServletContext에 바인딩해서, FrameworkServlet#service부터 HandlerMapping/HandlerAdapter
// 선택까지 전부 실제 코드 경로를 태운다 - standaloneSetup과 달리 @EnableWebMvc가 등록하는
// 기본 인프라(HttpRequestHandlerAdapter 등)까지 그대로 갖춘 실제 컨텍스트다.
class DispatcherServletTraceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceInterceptor.reset();
        BlockingInterceptor.reset();

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcTraceConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void restControllerSerializesReturnValueAsJson() throws Exception {
        mockMvc.perform(get("/users/42").param("detail", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.detail").value(true));
    }

    @Test
    void literalPathTakesPriorityOverVariablePath() throws Exception {
        // "/users/me"가 "/users/{id}"와 "id=me"로도 매칭될 수 있지만, 리터럴 경로가
        // 항상 이긴다.
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(0));
    }

    @Test
    void postWithRequestBodyIsDeserializedAndWrappedInResponseEntity() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(999));
    }

    @Test
    void nonExistentUrlReturns404WithoutThrowing() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unhandledControllerExceptionPropagatesOutOfDispatch() {
        // 컨트롤러가 던진 RuntimeException을 처리할 ExceptionResolver가 없으면(기본
        // DefaultHandlerExceptionResolver는 Spring이 아는 특정 예외만 처리한다), 예외가
        // doDispatch()를 빠져나가 그대로 호출자에게 전파된다.
        assertThatThrownBy(() -> mockMvc.perform(get("/users/boom")))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void plainControllerReturnsAViewNameInsteadOfSerializingTheBody() throws Exception {
        mockMvc.perform(get("/greeting"))
                .andExpect(status().isOk())
                .andExpect(view().name("greeting-view"))
                .andExpect(model().attribute("message", "hello"));
    }

    @Test
    void interceptorsFireInOrderAroundTheHandler() throws Exception {
        mockMvc.perform(get("/users/me"));

        assertThat(TraceInterceptor.events()).containsExactly("preHandle", "postHandle", "afterCompletion");
    }

    @Test
    void preHandleReturningFalseNeverReachesTheController() throws Exception {
        mockMvc.perform(get("/users/blocked"));

        assertThat(BlockingInterceptor.controllerReached).isFalse();
    }

    @Test
    void higherPriorityHandlerMappingWinsOverRequestMappingHandlerMapping() throws Exception {
        // priorityHandlerMapping(HIGHEST_PRECEDENCE)이 RequestMappingHandlerMapping(기본
        // order=0)보다 먼저 검사되므로, 같은 경로에 매핑된 UserController#priorityTest는
        // 아예 호출되지 않는다.
        mockMvc.perform(get("/users/priority-test"))
                .andExpect(status().isOk())
                .andExpect(content().string("from-simple-url-mapping"));
    }
}
