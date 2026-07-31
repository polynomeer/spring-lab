package lab.sampleapp.outbox;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// @ComponentScan 대신 명시적으로 @Import한다 - application-event-lab에서 실제로 겪었듯,
// 패키지 전체를 스캔하면 테스트 소스셋의 헬퍼 클래스까지 함께 주워 담길 위험이 있다.
@Configuration
@EnableTransactionManagement
@Import({
        OrderRepository.class, OutboxRepository.class, ProcessedMessageRepository.class,
        NaiveOrderService.class, NaiveOrderPlacedListener.class,
        OutboxOrderService.class, OutboxPublisher.class
})
public class OutboxSampleConfig {

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

    @Bean
    FakeMessageBroker messageBroker() {
        return new FakeMessageBroker();
    }
}
