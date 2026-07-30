package lab.minispring.webmvc;

import jakarta.servlet.http.HttpServletResponse;

public interface MiniHandlerExceptionResolver {

    // 처리했으면(응답을 이미 썼으면) true, 이 resolver가 모르는 예외라 다음 resolver나
    // 기본 처리로 넘겨야 하면 false.
    boolean resolveException(Object handler, Throwable ex, HttpServletResponse response) throws Exception;
}
