package lab.sampleapp.orderplatform.web;

import java.time.Instant;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import lab.ext.apiresponse.ApiResponse;

/**
 * project 26(spring-extensions/api-response-handler)의 ApiResponse&lt;T&gt;를 그대로
 * 재사용하되, 그 모듈의 ApiResponseBodyAdvice는 그대로 쓰지 않는다 - 그건 success를 항상
 * true로 고정하는 ApiResponse.of()만 쓰기 때문에(그 모듈은 오직 성공 응답 예시만 다룬다),
 * 이 모듈의 @ExceptionHandler가 돌려주는 ErrorResponse까지 같은 경로로 감싸면 실패
 * 응답인데도 "success": true로 나가 버린다. 그래서 body가 ErrorResponse인지 여부로
 * success를 직접 계산하는 이 모듈만의 advice를 둔다 - "재사용 vs 이 모듈의 요구사항"
 * 사이에서 타입(ApiResponse)은 재사용하고 그 타입을 감싸는 정책(성공 여부 판단)은
 * 새로 짠 경우다.
 */
@ControllerAdvice
public class OrderResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !ApiResponse.class.equals(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        boolean success = !(body instanceof ErrorResponse);
        return new ApiResponse<>(success, body, Instant.now());
    }
}
