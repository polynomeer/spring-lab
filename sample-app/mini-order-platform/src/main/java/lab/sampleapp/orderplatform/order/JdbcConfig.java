package lab.sampleapp.orderplatform.order;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Phase 3(트랜잭션)이 필요로 하는 인프라 빈만 모아 둔다 - DataSource/JdbcTemplate은
 * 컴포넌트 스캔 대상이 될 수 없는 @Bean 팩토리 메서드이므로, 다른 Phase의 @Component들과는
 * 별도로 명시적으로 등록한다. 매 테스트가 새 AnnotationConfigApplicationContext를 만들 때마다
 * EmbeddedDatabaseBuilder가 이름 없는(고유) H2 인스턴스를 새로 만들어 주므로 테스트 간
 * 데이터가 섞이지 않는다.
 */
@Configuration
@EnableTransactionManagement
public class JdbcConfig {

    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
