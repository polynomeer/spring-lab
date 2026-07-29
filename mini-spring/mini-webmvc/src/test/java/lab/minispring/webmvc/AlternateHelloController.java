package lab.minispring.webmvc;

// GreetingController와 똑같은 "GET /hello" 경로를 매핑해 둔다 - 여러 HandlerMapping이
// 순서대로 검사된다는 것을 보여주는 대조군이다.
class AlternateHelloController {

    @MiniRequestMapping(path = "/hello", method = "GET")
    public String hello() {
        return "alternate hello";
    }
}
