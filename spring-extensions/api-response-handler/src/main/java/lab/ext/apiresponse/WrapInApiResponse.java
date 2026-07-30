package lab.ext.apiresponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// ApiResponseReturnValueHandler(수동 래핑, ResponseBodyAdvice 체인을 타지 않음)가 적용될
// 메서드를 표시하는 마커 - ApiResponseBodyAdvice(전역 ResponseBodyAdvice)가 처리하는 메서드와
// 명확히 분리하기 위한 것이다.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WrapInApiResponse {
}
