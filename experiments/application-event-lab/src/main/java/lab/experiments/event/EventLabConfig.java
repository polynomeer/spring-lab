package lab.experiments.event;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// 패키지 전체를 훑는 @ComponentScan 대신 대상 컴포넌트를 명시적으로 @Import한다 - 테스트
// 소스셋의 헬퍼 @Configuration(예: ApplicationEventLabTest의 중첩 클래스)이 같은 패키지에
// 있으면 컴파일된 테스트 클래스도 런타임 클래스패스에 함께 올라오는 tests 실행 중에는
// @ComponentScan이 그것까지 함께 주워 담아 버리기 때문이다 - 실제로 겪은 문제다.
@Configuration
@EnableAsync
@EnableTransactionManagement
@Import({OrderService.class, OrderEventListeners.class, FailureProneListener.class})
public class EventLabConfig {

    @Bean
    EventLog eventLog() {
        return new EventLog();
    }

    // @TransactionalEventListener를 시험하려면 진짜 PlatformTransactionManager가 필요하다 -
    // 실제 SQL은 실행하지 않으므로 스키마 없이 빈 임베디드 DB만 띄운다.
    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
