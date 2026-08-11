package lab.sampleapp.orderplatform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductRepository;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentMethodConverterTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(OrderWebConfig.class);
        context.refresh();
        context.getBean(ProductRepository.class).save(new Product(901L, "product-901", 1000, 10));

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void aSupportedMethodConvertsAndTheRequestSucceeds() throws Exception {
        mockMvc.perform(post("/orders?method=POINT")
                        .header("X-Member-Id", "cust-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": 901, \"quantity\": 1}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void anEnumConstantThatNoGatewaySupportsIsRejectedAtTheWebLayerWithAConsistentErrorEnvelope() throws Exception {
        // BANK_TRANSFER는 PaymentMethod enum 상수로는 유효하다(Phase 1 참고) - 이 컨버터가
        // 그걸 거절하는지 실제로 확인한다. 결과는 OrderExceptionHandlers의 주석에 적어 둔
        // 대로다 - PaymentMethodConverter가 직접 던진 UnsupportedPaymentMethodException이
        // 아니라, Spring이 그걸 감싼 MethodArgumentTypeMismatchException을 잡아야 한다.
        mockMvc.perform(post("/orders?method=BANK_TRANSFER")
                        .header("X-Member-Id", "cust-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": 901, \"quantity\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("UNSUPPORTED_PAYMENT_METHOD"));
    }

    @Test
    void aCompletelyInvalidEnumValueAlsoGetsAConsistentErrorEnvelope() throws Exception {
        mockMvc.perform(post("/orders?method=NOT_A_REAL_METHOD")
                        .header("X-Member-Id", "cust-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": 901, \"quantity\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("UNSUPPORTED_PAYMENT_METHOD"));
    }
}
