package lab.ext.apiresponse;

import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

// HandlerMethodReturnValueHandler로 응답 래핑을 직접 구현한 버전 - ApiResponseBodyAdvice와
// 비교하기 위한 것이다(16번 절, "비교 대상" 참고).
//
// 실제로 메시지 변환(JSON 직렬화)을 수행하려면 HttpMessageConverter가 필요한데, 이 컨버터를
// 새로 만들지 않고 RequestResponseBodyMethodProcessor에 위임한다 - 그런데 이 위임 객체를
// "converters만 받는" 기본 생성자로 만들면, 그 javadoc이 명시하듯 ResponseBodyAdvice 체인이
// 전혀 적용되지 않는다. 즉 이 방식으로 등록된 컨트롤러 메서드는 전역 ResponseBodyAdvice를
// 우회한다 - ApiResponseBodyAdvice가 등록돼 있어도 이 경로에는 적용되지 않는다는 뜻이다.
public final class ApiResponseReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final RequestResponseBodyMethodProcessor delegate;

    public ApiResponseReturnValueHandler() {
        List<HttpMessageConverter<?>> converters = List.of(new MappingJackson2HttpMessageConverter());
        this.delegate = new RequestResponseBodyMethodProcessor(converters);
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return returnType.hasMethodAnnotation(WrapInApiResponse.class);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType,
            ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        ApiResponse<Object> wrapped = ApiResponse.of(returnValue);
        delegate.handleReturnValue(wrapped, returnType, mavContainer, webRequest);
    }
}
