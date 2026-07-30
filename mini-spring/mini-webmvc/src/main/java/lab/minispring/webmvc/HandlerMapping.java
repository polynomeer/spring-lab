package lab.minispring.webmvc;

import jakarta.servlet.http.HttpServletRequest;

public interface HandlerMapping {

    // 실제 Spring의 HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE에 대응한다 - 매핑 단계가
    // 추출한 경로 변수를, 나중에 인자 해석 단계(PathVariableArgumentResolver)가 다시 조회할
    // 수 있도록 request 속성이라는 채널에 남겨 둔다.
    String PATH_VARIABLES_ATTRIBUTE = "lab.minispring.webmvc.pathVariables";

    // 매칭되는 핸들러가 없으면 null - 실제 Spring도 여러 HandlerMapping을 order대로 훑다가
    // 첫 null 아닌 응답을 채택한다(15주차 문서, 여러 HandlerMapping 우선순위 실험 참고).
    HandlerMethod getHandler(HttpServletRequest request);
}
