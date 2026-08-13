package lab.experiments.testcontext;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 클래스 안의 @Test 메서드들은 (기본적으로) 같은 ApplicationContext 인스턴스를 공유한다 -
// 그 컨텍스트 안의 싱글턴 빈은 메서드 사이에 자동으로 리셋되지 않는다는 것을, 상태를 직접
// 남겨서 확인한다. @TestMethodOrder로 순서를 고정해야 이 실험 자체가 결정론적이다 - JUnit은
// 기본적으로 메서드 실행 순서를 보장하지 않는다.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BeanStatePersistsAcrossTestMethodsTest.MutableCounterConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BeanStatePersistsAcrossTestMethodsTest {

    @Autowired
    private MutableCounter counter;

    @Test
    @Order(1)
    void firstMethodIncrementsTheSharedSingleton() {
        counter.increment();

        assertThat(counter.value()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void secondMethodSeesTheMutationLeftByTheFirstMethod() {
        // 새 컨텍스트가 아니라 첫 번째 테스트가 쓰던 바로 그 컨텍스트, 그 바로 그 빈 인스턴스다
        // - 값이 0으로 리셋돼 있지 않다.
        assertThat(counter.value()).isEqualTo(1);

        counter.increment();

        assertThat(counter.value()).isEqualTo(2);
    }

    static class MutableCounter {

        private int value;

        void increment() {
            value++;
        }

        int value() {
            return value;
        }
    }

    @Configuration
    static class MutableCounterConfig {

        @Bean
        MutableCounter mutableCounter() {
            return new MutableCounter();
        }
    }
}
