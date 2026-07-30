package lab.ext.apiresponse;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// ApiResponseReturnValueHandler와 달리, 이건 기존 @ResponseBody 처리 경로(기본
// RequestResponseBodyMethodProcessor) "안에" 끼어드는 확장점이다 - 메시지 변환 인프라를
// 새로 만들 필요가 전혀 없다. @ControllerAdvice로 등록되면 컨테이너의 모든 @ResponseBody
// 응답에 자동으로 적용된다(WrapInApiResponse로 표시된, 별도 경로로 처리되는 메서드는
// 애초에 이 체인을 타지 않으므로 제외할 필요조차 없다).
@ControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !ApiResponse.class.equals(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        return ApiResponse.of(body);
    }
}
