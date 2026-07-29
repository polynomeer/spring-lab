package lab.minispring.webmvc;

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

        dispatcherServlet = new MiniDispatcherServlet();
        dispatcherServlet.addHandlerMapping(mapping);
        dispatcherServlet.addHandlerAdapter(new HandlerMethodAdapter());
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
    void controllerExceptionResultsIn500() throws Exception {
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
}
