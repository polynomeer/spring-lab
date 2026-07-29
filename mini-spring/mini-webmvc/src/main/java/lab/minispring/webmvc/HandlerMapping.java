package lab.minispring.webmvc;

import jakarta.servlet.http.HttpServletRequest;

public interface HandlerMapping {

    // 매칭되는 핸들러가 없으면 null - 실제 Spring도 여러 HandlerMapping을 order대로 훑다가
    // 첫 null 아닌 응답을 채택한다(15주차 문서, 여러 HandlerMapping 우선순위 실험 참고).
    HandlerMethod getHandler(HttpServletRequest request);
}
