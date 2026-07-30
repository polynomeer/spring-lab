package lab.minispring.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// HandlerMapping과 분리한 이유는 실제 Spring과 같다 - "이 요청에 어떤 핸들러가 맞는가"와
// "그 핸들러를 실제로 어떻게 호출하는가"는 서로 다른 관심사다. 핸들러의 형태(HandlerMethod,
// 함수형 핸들러, 정적 리소스 등)마다 호출 방식이 다르므로, HandlerMapping은 핸들러를
// "찾기"만 하고 HandlerAdapter가 그 핸들러의 종류에 맞게 "호출"을 담당한다.
public interface HandlerAdapter {

    boolean supports(Object handler);

    // 반환값은 이제 없다(16주차부터) - 핸들러를 호출한 결과를 응답에 쓰는 책임 자체가
    // ReturnValueHandler로 넘어갔기 때문이다. MiniDispatcherServlet은 더 이상 반환값을
    // 알 필요가 없다.
    void handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;
}
