package lab.minispring.webmvc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletResponse;

final class FakeHttpServletResponse implements InvocationHandler {

    private int status = HttpServletResponse.SC_OK;
    private String contentType;
    private final StringWriter body = new StringWriter();
    private final PrintWriter writer = new PrintWriter(body);

    int status() {
        return status;
    }

    String body() {
        writer.flush();
        return body.toString();
    }

    String contentType() {
        return contentType;
    }

    static Fake create() {
        FakeHttpServletResponse state = new FakeHttpServletResponse();
        HttpServletResponse proxy = (HttpServletResponse) Proxy.newProxyInstance(
                FakeHttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                state);
        return new Fake(proxy, state);
    }

    record Fake(HttpServletResponse response, FakeHttpServletResponse state) {
    }

    @Override
    public Object invoke(Object proxy, Method invokedMethod, Object[] args) {
        switch (invokedMethod.getName()) {
            case "setStatus":
                status = (int) args[0];
                return null;
            case "getStatus":
                return status;
            case "setContentType":
                contentType = (String) args[0];
                return null;
            case "getContentType":
                return contentType;
            case "getWriter":
                return writer;
            default:
                throw new UnsupportedOperationException(invokedMethod.getName());
        }
    }
}
