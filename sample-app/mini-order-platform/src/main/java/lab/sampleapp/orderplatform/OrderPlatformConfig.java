package lab.sampleapp.orderplatform;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Phase 1~3(비-웹) 테스트가 계속 써 온 순수 AnnotationConfigApplicationContext용 설정이다.
 * web 패키지(Phase 4, @EnableWebMvc)를 명시적으로 제외한다 - @EnableWebMvc가 등록하는
 * 빈들은 실제 서블릿 컨텍스트 없이도 대체로 생성은 되지만, 이 모듈은 "웹 없이 쓰는 컨텍스트"와
 * "웹으로 쓰는 컨텍스트"를 아예 별도 설정 클래스(OrderWebConfig)로 분리해 두는 쪽을 택했다
 * - spring-extensions/current-user-argument-resolver, api-response-handler가 이미 같은
 * 이유로 MVC 설정을 좁게 스캔한 전례를 그대로 따른 것이다.
 */
@Configuration
@ComponentScan(
        basePackages = "lab.sampleapp.orderplatform",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX, pattern = "lab\\.sampleapp\\.orderplatform\\.web\\..*"))
public class OrderPlatformConfig {
}
