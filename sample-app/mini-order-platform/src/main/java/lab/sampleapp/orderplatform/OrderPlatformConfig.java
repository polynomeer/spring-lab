package lab.sampleapp.orderplatform;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import lab.sampleapp.orderplatform.boot.OrderPlatformApplication;

/**
 * Phase 1~3, 5(비-웹) 테스트가 계속 써 온 순수 AnnotationConfigApplicationContext용 설정이다.
 * web 패키지(Phase 4, @EnableWebMvc)를 명시적으로 제외한다 - @EnableWebMvc가 등록하는
 * 빈들은 실제 서블릿 컨텍스트 없이도 대체로 생성은 되지만, 이 모듈은 "웹 없이 쓰는 컨텍스트"와
 * "웹으로 쓰는 컨텍스트"를 아예 별도 설정 클래스(OrderWebConfig)로 분리해 두는 쪽을 택했다
 * - spring-extensions/current-user-argument-resolver, api-response-handler가 이미 같은
 * 이유로 MVC 설정을 좁게 스캔한 전례를 그대로 따른 것이다.
 *
 * <p><b>직접 겪은 함정(Phase 6)</b>: OrderPlatformApplication도 명시적으로 제외해야 한다 -
 * 그 클래스는 boot 패키지에 있어서(다른 @AutoConfiguration들과 같은 패키지) 이 스캔에
 * 그대로 걸리는데, 그 클래스에 붙은 {@code @EnableAutoConfiguration}은 컴포넌트 스캔으로
 * "발견되기만 해도" 그대로 활성화된다 - Spring Boot의 표준 DataSourceAutoConfiguration,
 * SqlInitializationAutoConfiguration 등 전부가 이 순수 테스트 컨텍스트 안으로 통째로
 * 끌려들어와서, JdbcConfig가 이미 실행해 둔 schema.sql을 Boot가 또 한 번 실행하려다가
 * "테이블이 이미 있다"로 깨지는 걸 직접 봤다. @SpringBootApplication류 진입점 클래스는
 * 자신이 루트가 되는 스캔에만 등장해야지, 다른 목적의 컴포넌트 스캔에 우연히 휩쓸리면 안
 * 된다는 걸 이번에 실제로 겪었다.
 */
@Configuration
@ComponentScan(
        basePackages = "lab.sampleapp.orderplatform",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "lab\\.sampleapp\\.orderplatform\\.web\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE, classes = OrderPlatformApplication.class)
        })
public class OrderPlatformConfig {
}
