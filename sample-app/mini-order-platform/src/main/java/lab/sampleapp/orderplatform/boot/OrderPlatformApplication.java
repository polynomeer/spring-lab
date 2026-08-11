package lab.sampleapp.orderplatform.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import lab.sampleapp.orderplatform.web.OrderWebConfig;

/**
 * 이 캡스톤 전체(Phase 1~5)를 실제 Spring Boot 애플리케이션으로 띄우는 진입점이다.
 * {@code @SpringBootApplication}(과 그 안의 암시적 @ComponentScan) 대신 {@code @Configuration
 * + @EnableAutoConfiguration + @Import}를 명시적으로 조합했다 - OrderWebConfig가 이미
 * "lab.sampleapp.orderplatform" 전체를 스캔하므로, 여기서 또 스캔 루트를 하나 더 만들면
 * 같은 패키지를 두 번 스캔하는 것뿐이라 의미가 없다.
 *
 * <p>{@code @EnableAutoConfiguration}이 하는 일은 두 가지다: (1) 이 모듈 자신의
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports에 있는
 * PaymentGatewayAutoConfiguration/NotificationAutoConfiguration을 찾아 등록하고(사실 이건
 * OrderWebConfig의 컴포넌트 스캔만으로도 이미 등록됐을 것이다), (2) 클래스패스에 있는
 * spring-extensions/mini-observability-starter의 RequestObservationAutoConfiguration과
 * Boot 표준 웹 자동 설정(내장 톰캣, DispatcherServlet 등)까지 전부 찾아 등록한다 - 우리는
 * 이 인터셉터를 위한 배선 코드를 단 한 줄도 쓰지 않았다.
 *
 * <p><b>직접 겪은 함정</b>: {@code SqlInitializationAutoConfiguration}은 제외해야 한다 -
 * Boot는 클래스패스에서 schema.sql을 자동으로 찾아 실행해 주는데, JdbcConfig#dataSource()가
 * EmbeddedDatabaseBuilder#addScript()로 이미 그 파일을 실행해 둔 DataSource 빈을 그대로
 * 재사용한다(@ConditionalOnMissingBean 덕분에 Boot가 새 DataSource를 만들지는 않는다).
 * 문제는 "같은 DataSource에 같은 schema.sql을 두 번" 실행하는 것 자체다 - Boot의 SQL
 * 초기화는 우리가 이미 끝낸 일을 또 하려다가 "테이블이 이미 있다"로 실패한다.
 */
@Configuration
@EnableAutoConfiguration(exclude = SqlInitializationAutoConfiguration.class)
@Import(OrderWebConfig.class)
public class OrderPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderPlatformApplication.class, args);
    }
}
