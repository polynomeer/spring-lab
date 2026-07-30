package lab.minispring.webmvc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

// 실제 RequestMappingHandlerMapping의 극단적 축소판. 15주차에는 (경로, HTTP 메서드) 문자열의
// 완전 일치만 지원했는데, 이번 주(4단계)는 "{name}" 형태의 경로 변수를 지원하도록 확장했다.
//
// Class#getMethods()의 순회 순서는 JVM이 보장하지 않는다 - 처음에는 "등록 순서대로 첫 매칭을
// 채택한다"고 안이하게 가정했다가, 같은 세그먼트 수를 가진 "/api/users/{id}"와
// "/api/users/wrapped"가 어느 쪽이 먼저 등록되느냐에 따라 죽었다 살았다 하는 테스트를 직접
// 겪었다 - 15주차 real-Spring 실험에서 확인한 PathPattern.SPECIFICITY_COMPARATOR가 왜
// 필요한지를 몸으로 확인한 셈이다. 변수 세그먼트 개수가 적을수록(리터럴에 가까울수록) 먼저
// 검사하도록 정렬해서 고쳤다 - 실제 Spring만큼 정교하진 않지만(하나의 변수 vs 두 개의 변수
// 정도만 구분), "등록 순서에 결과가 좌우돼서는 안 된다"는 핵심은 재현한다.
public final class AnnotationHandlerMapping implements HandlerMapping {

    private record RegisteredHandler(String httpMethod, String[] patternSegments, HandlerMethod handlerMethod) {
    }

    private final List<RegisteredHandler> handlers = new ArrayList<>();

    public void registerController(Object controller) {
        for (Method method : controller.getClass().getMethods()) {
            MiniRequestMapping mapping = method.getAnnotation(MiniRequestMapping.class);
            if (mapping != null) {
                handlers.add(new RegisteredHandler(
                        mapping.method().toUpperCase(),
                        mapping.path().split("/"),
                        new HandlerMethod(controller, method)));
            }
        }
        handlers.sort(Comparator.comparingInt(AnnotationHandlerMapping::variableSegmentCount));
    }

    private static int variableSegmentCount(RegisteredHandler handler) {
        int count = 0;
        for (String segment : handler.patternSegments()) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                count++;
            }
        }
        return count;
    }

    @Override
    public HandlerMethod getHandler(HttpServletRequest request) {
        String httpMethod = request.getMethod().toUpperCase();
        String[] pathSegments = request.getRequestURI().split("/");

        for (RegisteredHandler registered : handlers) {
            if (!registered.httpMethod().equals(httpMethod)) {
                continue;
            }
            Map<String, String> variables = match(registered.patternSegments(), pathSegments);
            if (variables != null) {
                request.setAttribute(PATH_VARIABLES_ATTRIBUTE, variables);
                return registered.handlerMethod();
            }
        }
        return null;
    }

    private Map<String, String> match(String[] patternSegments, String[] pathSegments) {
        if (patternSegments.length != pathSegments.length) {
            return null;
        }
        Map<String, String> variables = new HashMap<>();
        for (int i = 0; i < patternSegments.length; i++) {
            String pattern = patternSegments[i];
            String actual = pathSegments[i];
            if (pattern.startsWith("{") && pattern.endsWith("}")) {
                variables.put(pattern.substring(1, pattern.length() - 1), actual);
            } else if (!pattern.equals(actual)) {
                return null;
            }
        }
        return variables;
    }
}
