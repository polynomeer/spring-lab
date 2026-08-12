package lab.minispring.webmvc;

import java.lang.reflect.Parameter;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;

// HandlerMethodAdapter의 리졸버 매칭 캐시를 검증하기 위한 테스트 지원 클래스 - 실제 판단은
// delegate에게 그대로 맡기고, supports() 호출 횟수만 센다.
final class CountingArgumentResolver implements MiniArgumentResolver {

    private final MiniArgumentResolver delegate;
    private final AtomicInteger supportsCallCount = new AtomicInteger();

    CountingArgumentResolver(MiniArgumentResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean supports(Parameter parameter) {
        supportsCallCount.incrementAndGet();
        return delegate.supports(parameter);
    }

    @Override
    public Object resolve(Parameter parameter, HttpServletRequest request) throws Exception {
        return delegate.resolve(parameter, request);
    }

    int supportsCallCount() {
        return supportsCallCount.get();
    }
}
