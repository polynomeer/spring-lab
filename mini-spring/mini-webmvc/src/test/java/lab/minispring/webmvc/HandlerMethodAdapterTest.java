package lab.minispring.webmvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlerMethodAdapterTest {

    @Test
    void resolverMatchIsCachedAfterTheFirstRequestSoSupportsIsNotCalledAgain() throws Exception {
        CountingArgumentResolver pathVariableResolver = new CountingArgumentResolver(new PathVariableArgumentResolver());
        CountingArgumentResolver requestParamResolver = new CountingArgumentResolver(new RequestParamArgumentResolver());
        List<MiniArgumentResolver> argumentResolvers = List.of(pathVariableResolver, requestParamResolver);
        List<MiniReturnValueHandler> returnValueHandlers = List.of(new JsonReturnValueHandler());
        HandlerMethodAdapter adapter = new HandlerMethodAdapter(argumentResolvers, returnValueHandlers);

        Method method = UserApiController.class.getMethod("find", long.class, Boolean.class);
        HandlerMethod handlerMethod = new HandlerMethod(new UserApiController(), method);

        for (int i = 0; i < 3; i++) {
            HttpServletRequest request =
                    FakeHttpServletRequest.create("GET", "/api/users/7", Map.of("detail", "true"));
            request.setAttribute(HandlerMapping.PATH_VARIABLES_ATTRIBUTE, Map.of("id", "7"));
            FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

            adapter.handle(request, response.response(), handlerMethod);

            // 캐시가 잘못돼도 매 요청의 값 자체는 여전히 올바르게 해석돼야 한다.
            assertThat(response.state().body()).isEqualTo("{\"id\":7,\"detail\":true}");
        }

        // id 파라미터는 pathVariableResolver가 첫 요청에서 바로 매칭되므로 1번만 물어봄.
        // detail 파라미터는 pathVariableResolver가 아니라고 답한 뒤(1번) requestParamResolver가
        // 맞다고 답하는(1번) 과정이 첫 요청에서만 일어난다 - 그래서 pathVariableResolver는
        // (id 1번 + detail 1번) = 2번, requestParamResolver는 (detail 1번) = 1번, 이후 두 번의
        // 요청에서는 캐시를 타므로 두 리졸버 모두 더 이상 호출되지 않는다.
        assertThat(pathVariableResolver.supportsCallCount()).isEqualTo(2);
        assertThat(requestParamResolver.supportsCallCount()).isEqualTo(1);
    }

    @Test
    void handlerMethodAdapterStillThrowsWhenNoResolverSupportsAParameter() throws Exception {
        HandlerMethodAdapter adapter = new HandlerMethodAdapter(List.of(), List.of(new JsonReturnValueHandler()));
        Method method = UserApiController.class.getMethod("find", long.class, Boolean.class);
        HandlerMethod handlerMethod = new HandlerMethod(new UserApiController(), method);
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/api/users/7");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        assertThatThrownBy(() -> adapter.handle(request, response.response(), handlerMethod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no ArgumentResolver for parameter");
    }
}
