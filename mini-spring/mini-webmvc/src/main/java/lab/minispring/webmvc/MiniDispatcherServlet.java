package lab.minispring.webmvc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 실제 DispatcherServlet의 극단적 축소판 - Front Controller 패턴 하나만 보여준다.
// HandlerMapping 목록을 순서대로 훑어 첫 매칭을 찾고(우선순위), 그 핸들러를 처리할 수 있는
// HandlerAdapter를 찾아 위임한다. ReturnValueHandler(16주차)가 아직 없어서 반환값은
// toString()으로 그냥 응답 본문에 쓴다.
public final class MiniDispatcherServlet extends HttpServlet {

    private final List<HandlerMapping> handlerMappings = new ArrayList<>();
    private final List<HandlerAdapter> handlerAdapters = new ArrayList<>();

    public void addHandlerMapping(HandlerMapping handlerMapping) {
        handlerMappings.add(handlerMapping);
    }

    public void addHandlerAdapter(HandlerAdapter handlerAdapter) {
        handlerAdapters.add(handlerAdapter);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Object handler = findHandler(request);
        if (handler == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        HandlerAdapter adapter = findAdapter(handler);
        try {
            Object result = adapter.handle(request, response, handler);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(String.valueOf(result));
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Object findHandler(HttpServletRequest request) {
        for (HandlerMapping mapping : handlerMappings) {
            HandlerMethod handler = mapping.getHandler(request);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    private HandlerAdapter findAdapter(Object handler) {
        for (HandlerAdapter adapter : handlerAdapters) {
            if (adapter.supports(handler)) {
                return adapter;
            }
        }
        throw new IllegalStateException("no HandlerAdapter for handler: " + handler);
    }
}
