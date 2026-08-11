package lab.sampleapp.orderplatform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductWebIntegrationTest {

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
    void adminCanCreateAProductAndAnyoneCanReadItBack() throws Exception {
        mockMvc.perform(post("/products")
                        .header("X-Member-Id", "admin-1")
                        .header("X-Member-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\": \"widget\", \"priceWon\": 3000, \"stock\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("widget"))
                .andExpect(jsonPath("$.data.stock").value(10));

        // 조회는 인증 헤더 없이도 된다 - GetMapping 메서드들은 @CurrentMember를 선언하지 않는다.
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("widget"));
    }

    @Test
    void customerCannotCreateAProduct() throws Exception {
        mockMvc.perform(post("/products")
                        .header("X-Member-Id", "cust-1")
                        .header("X-Member-Role", "CUSTOMER")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\": \"widget\", \"priceWon\": 3000, \"stock\": 10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("ACCESS_DENIED"));
    }

    @Test
    void fetchingAMissingProductReturns404() throws Exception {
        mockMvc.perform(get("/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("PRODUCT_NOT_FOUND"));
    }
}
