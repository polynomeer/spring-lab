package lab.minispring.webmvc;

import jakarta.servlet.http.HttpServletRequest;

class GreetingController {

    @MiniRequestMapping(path = "/hello", method = "GET")
    public String hello() {
        return "hello mini mvc";
    }

    @MiniRequestMapping(path = "/echo-method", method = "GET")
    public String echoMethod(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    @MiniRequestMapping(path = "/boom", method = "GET")
    public String boom() {
        throw new IllegalStateException("controller exploded");
    }
}
