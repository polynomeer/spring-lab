package lab.ext.currentuser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurrentUserArgumentResolverTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(CurrentUserConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void resolvesTheAuthenticatedUserFromHeaders() throws Exception {
        mockMvc.perform(get("/me").header("X-User-Id", "42").header("X-User-Name", "Ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.name").value("Ada"));
    }

    @Test
    void requiredCurrentUserMissingResultsIn401() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionalCurrentUserMissingResolvesToNullWithoutError() throws Exception {
        mockMvc.perform(get("/me/optional"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    void optionalCurrentUserPresentIsStillResolved() throws Exception {
        mockMvc.perform(get("/me/optional").header("X-User-Id", "7").header("X-User-Name", "Grace"))
                .andExpect(status().isOk())
                .andExpect(content().string("Grace"));
    }
}
