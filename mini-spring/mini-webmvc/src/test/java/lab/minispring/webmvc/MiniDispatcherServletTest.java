package lab.minispring.webmvc;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class MiniDispatcherServletTest {

    private MiniDispatcherServlet dispatcherServlet;

    @BeforeEach
    void setUp() {
        AnnotationHandlerMapping mapping = new AnnotationHandlerMapping();
        mapping.registerController(new GreetingController());
        mapping.registerController(new UserApiController());

        List<MiniArgumentResolver> argumentResolvers = List.of(
                new PathVariableArgumentResolver(),
                new RequestParamArgumentResolver(),
                new RequestBodyArgumentResolver(),
                new ServletRequestArgumentResolver());
        List<MiniReturnValueHandler> returnValueHandlers = List.of(
                new StringReturnValueHandler(),
                new ResponseEntityReturnValueHandler(),
                new JsonReturnValueHandler());

        dispatcherServlet = new MiniDispatcherServlet();
        dispatcherServlet.addHandlerMapping(mapping);
        dispatcherServlet.addHandlerAdapter(new HandlerMethodAdapter(argumentResolvers, returnValueHandlers));
        dispatcherServlet.addExceptionResolver(new AnnotationExceptionResolver(returnValueHandlers));
    }

    @Test
    void dispatchesToTheMatchingHandlerMethod() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/hello");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(200);
        assertThat(response.state().body()).isEqualTo("hello mini mvc");
    }

    @Test
    void passesTheRawRequestToAHandlerMethodThatAcceptsIt() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/echo-method");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().body()).isEqualTo("GET /echo-method");
    }

    @Test
    void unmatchedUrlReturns404() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/does-not-exist");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(404);
    }

    @Test
    void controllerExceptionWithoutAMatchingExceptionHandlerResultsIn500() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/boom");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(500);
    }

    @Test
    void firstRegisteredHandlerMappingWinsWhenBothMatch() throws Exception {
        AnnotationHandlerMapping secondMapping = new AnnotationHandlerMapping();
        secondMapping.registerController(new AlternateHelloController());
        // 이미 GreetingController가 매핑된 뒤에 추가했으므로, 순서상 두 번째로 검사된다.
        dispatcherServlet.addHandlerMapping(secondMapping);

        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/hello");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().body()).isEqualTo("hello mini mvc");
    }

    @Test
    void pathVariableAndOptionalRequestParamAreResolvedAndSerializedAsJson() throws Exception {
        HttpServletRequest request =
                FakeHttpServletRequest.create("GET", "/api/users/7", Map.of("detail", "true"));
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(200);
        assertThat(response.state().body()).isEqualTo("{\"id\":7,\"detail\":true}");
    }

    @Test
    void missingOptionalRequestParamResolvesToFalse() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/api/users/7");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().body()).isEqualTo("{\"id\":7,\"detail\":false}");
    }

    @Test
    void requestBodyIsBoundAsARawString() throws Exception {
        HttpServletRequest request =
                FakeHttpServletRequest.createWithBody("POST", "/api/users/echo-body", "raw payload");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().body()).isEqualTo("received:raw payload");
    }

    @Test
    void requestBodyIsDeserializedIntoARecordAndEchoedBackAsJson() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.createWithBody(
                "POST", "/api/users", "{\"id\":7,\"detail\":true}");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(200);
        assertThat(response.state().body()).isEqualTo("{\"id\":7,\"detail\":true}");
    }

    @Test
    void requestBodyWithANestedRecordFieldIsDeserializedRecursively() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.createWithBody("POST", "/api/users/with-address",
                "{\"id\":1,\"name\":\"ada\",\"address\":{\"street\":\"1 Infinite Loop\",\"city\":\"Cupertino\"}}");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(200);
        assertThat(response.state().body())
                .isEqualTo("{\"id\":1,\"name\":\"ada\",\"address\":{\"street\":\"1 Infinite Loop\",\"city\":\"Cupertino\"}}");
    }

    @Test
    void responseEntityControlsTheStatusCode() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/api/users/wrapped");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        assertThat(response.state().status()).isEqualTo(201);
        assertThat(response.state().body()).isEqualTo("{\"id\":999,\"detail\":false}");
    }

    @Test
    void exceptionHandlerOnTheSameControllerInterceptsTheThrownException() throws Exception {
        HttpServletRequest request = FakeHttpServletRequest.create("GET", "/api/users/boom");
        FakeHttpServletResponse.Fake response = FakeHttpServletResponse.create();

        dispatcherServlet.service(request, response.response());

        // 500이 아니라, @MiniExceptionHandler가 정상적으로 응답을 만들어 낸다.
        assertThat(response.state().status()).isEqualTo(200);
        assertThat(response.state().body()).isEqualTo("handled: controller exploded");
    }
}
