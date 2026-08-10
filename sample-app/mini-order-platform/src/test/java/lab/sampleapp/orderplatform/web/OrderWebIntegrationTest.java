package lab.sampleapp.orderplatform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Phase 4(MVC) 검증. Phase 1~3 테스트와 달리 순수 AnnotationConfigApplicationContext가
 * 아니라 AnnotationConfigWebApplicationContext + MockMvc를 쓴다 - project 25/26
 * (current-user-argument-resolver/api-response-handler)이 이미 확립한 패턴 그대로다.
 *
 * <p>직접 겪은 함정: JdbcConfig#dataSource()의 EmbeddedDatabaseBuilder는 이름을 지정하지
 * 않으면 항상 같은 기본 이름("testdb")을 쓴다 - 컨텍스트를 닫지 않고 다음 테스트로 넘어가면
 * 이전 테스트의 인메모리 DB가 살아있는 채로 다음 컨텍스트가 같은 이름으로 schema.sql을
 * 다시 실행하려다가 "테이블이 이미 있다"는 SQL 문법 오류로 깨진다 - @AfterEach에서
 * context.close()를 빼먹었을 때 실제로 이 오류를 만났다.
 */
class OrderWebIntegrationTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(OrderWebConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void placingAnOrderResolvesTheCurrentMemberAndWrapsTheSuccessResponse() throws Exception {
        mockMvc.perform(post("/orders?method=CARD")
                        .header("X-Member-Id", "cust-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amountWon\": 10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value("cust-1"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void placingAnOrderWithoutAMemberHeaderIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/orders?method=CARD")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amountWon\": 10000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("UNAUTHENTICATED"));
    }

    @Test
    void fetchingAMissingOrderReturnsAWrappedErrorResponse() throws Exception {
        mockMvc.perform(get("/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void refundRequiresAdminRoleEvenThoughTheControllerLayerDoesNotCheckItItself() throws Exception {
        mockMvc.perform(post("/payments/refund")
                        .header("X-Member-Id", "cust-1")
                        .header("X-Member-Role", "CUSTOMER")
                        .contentType(APPLICATION_JSON)
                        .content("{\"memberId\": \"cust-1\", \"amountWon\": 5000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/payments/refund")
                        .header("X-Member-Id", "admin-1")
                        .header("X-Member-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"memberId\": \"cust-1\", \"amountWon\": 5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void currentActorDoesNotLeakFromOneRequestToTheNextOnTheSameThread() throws Exception {
        // 같은 MockMvc(따라서 같은 스레드)로 먼저 인증된 요청을 보내 CurrentActor를 채운 뒤,
        // 헤더 없는 요청을 보낸다 - CurrentActorClearingInterceptor가 없다면 두 번째 요청이
        // 첫 번째 요청의 액터를 그대로 이어받아 401 대신 200이 나왔을 것이다.
        mockMvc.perform(post("/orders?method=CARD")
                        .header("X-Member-Id", "cust-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amountWon\": 10000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders?method=CARD")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amountWon\": 10000}"))
                .andExpect(status().isUnauthorized());
    }
}
