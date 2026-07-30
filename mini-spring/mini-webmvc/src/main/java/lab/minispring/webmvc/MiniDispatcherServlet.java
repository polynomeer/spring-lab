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
// HandlerAdapter를 찾아 위임한다. 6단계부터는 핸들러(또는 인자 해석)가 던진 예외를
// HandlerExceptionResolver 목록에 순서대로 넘겨 보고, 아무도 처리하지 못하면 기본 500으로
// 떨어진다 - 실제 DispatcherServlet이 여러 HandlerExceptionResolver를 체이닝하는 것과 같은
// 구조다.
public final class MiniDispatcherServlet extends HttpServlet {

    private final List<HandlerMapping> handlerMappings = new ArrayList<>();
    private final List<HandlerAdapter> handlerAdapters = new ArrayList<>();
    private final List<MiniHandlerExceptionResolver> exceptionResolvers = new ArrayList<>();

    public void addHandlerMapping(HandlerMapping handlerMapping) {
        handlerMappings.add(handlerMapping);
    }

    public void addHandlerAdapter(HandlerAdapter handlerAdapter) {
        handlerAdapters.add(handlerAdapter);
    }

    public void addExceptionResolver(MiniHandlerExceptionResolver exceptionResolver) {
        exceptionResolvers.add(exceptionResolver);
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
            adapter.handle(request, response, handler);
        } catch (Exception ex) {
            if (!tryResolveException(handler, ex, response)) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private boolean tryResolveException(Object handler, Exception ex, HttpServletResponse response) {
        for (MiniHandlerExceptionResolver resolver : exceptionResolvers) {
            try {
                if (resolver.resolveException(handler, ex, response)) {
                    return true;
                }
            } catch (Exception resolverFailure) {
                // 이 resolver가 실패해도 다음 resolver를 계속 시도한다.
            }
        }
        return false;
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
