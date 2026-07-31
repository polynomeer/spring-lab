package lab.experiments.event;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationEventLabTest {

    private AnnotationConfigApplicationContext context;
    private OrderService orderService;
    private EventLog eventLog;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(EventLabConfig.class);
        orderService = context.getBean(OrderService.class);
        eventLog = context.getBean(EventLog.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void syncListenersRunOnThePublisherThreadInOrderDeclaredByAtOrder() {
        orderService.completeOrder(2);

        var syncLabels = eventLog.entries().stream()
                .map(EventLog.Entry::label)
                .filter(label -> !label.equals("async"))
                .toList();

        // @Order(1, 10, 20, 30, 40) - 등록된 클래스와 무관하게 전역으로 정렬된다.
        assertThat(syncLabels).containsExactly(
                "failure-prone-first", "sync-first", "sync-second", "condition-even", "failure-prone-last");
        assertThat(eventLog.entries())
                .filteredOn(entry -> !entry.label().equals("async"))
                .allSatisfy(entry -> assertThat(entry.threadName()).isEqualTo(Thread.currentThread().getName()));
    }

    @Test
    void conditionSkipsTheListenerWhenTheSpelExpressionIsFalse() {
        orderService.completeOrder(3);

        var syncLabels = eventLog.entries().stream()
                .map(EventLog.Entry::label)
                .filter(label -> !label.equals("async"))
                .toList();

        assertThat(syncLabels).containsExactly("failure-prone-first", "sync-first", "sync-second", "failure-prone-last");
    }

    @Test
    void asyncListenerRunsOnADifferentThreadThanThePublisher() {
        orderService.completeOrder(5);

        boolean arrived = eventLog.awaitAtLeast(5, Duration.ofSeconds(2));
        assertThat(arrived).isTrue();

        var asyncEntry = eventLog.entries().stream()
                .filter(entry -> entry.label().equals("async"))
                .findFirst()
                .orElseThrow();
        assertThat(asyncEntry.threadName()).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void listenerExceptionAbortsTheMulticastLoopAndPropagatesToThePublisher() {
        // FailureProneListener#explodesOnNegativeId는 @Order(1)로 가장 먼저 실행된다 - 여기서
        // 예외가 나면 SimpleApplicationEventMulticaster의 errorHandler가 없으므로 그대로 던져지고,
        // 이후 순서(sync-first 등)의 리스너는 전혀 호출되지 않는다.
        assertThatThrownBy(() -> orderService.completeOrder(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative order id");

        assertThat(eventLog.entries()).isEmpty();
    }

    @Test
    void transactionalEventListenerFiresOnlyAfterActualCommit() {
        orderService.completeOrderInTransaction(10, false);

        assertThat(eventLog.entries())
                .extracting(EventLog.Entry::label)
                .contains("sync-first", "after-commit");
    }

    @Test
    void transactionalEventListenerDoesNotFireWhenTheTransactionRollsBack() {
        assertThatThrownBy(() -> orderService.completeOrderInTransaction(11, true))
                .isInstanceOf(IllegalStateException.class);

        // 일반 @EventListener는 publishEvent() 시점에 즉시(동기) 실행되므로 롤백 여부와
        // 무관하게 이미 실행됐다 - 반면 @TransactionalEventListener(AFTER_COMMIT)는 커밋이
        // 실제로 일어나야만 실행되므로, 롤백된 이 경우엔 절대 나타나지 않는다.
        assertThat(eventLog.entries())
                .extracting(EventLog.Entry::label)
                .contains("sync-first")
                .doesNotContain("after-commit");
    }

    @Test
    void eventPublishedInAChildContextAlsoReachesTheParentContextsListeners() {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(ParentListenerConfig.class)) {
            EventLog parentLog = parent.getBean(EventLog.class);

            try (AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext()) {
                child.setParent(parent);
                child.register(EventLabConfig.class);
                child.refresh();

                child.getBean(OrderService.class).completeOrder(99);

                // AbstractApplicationContext#publishEvent가 로컬 멀티캐스트 후 부모 컨텍스트에도
                // 재귀적으로 publishEvent를 호출한다 - 부모의 리스너도 자식이 발행한 이벤트를 받는다.
                assertThat(parentLog.entries())
                        .extracting(EventLog.Entry::label)
                        .contains("parent-received-99");
            }
        }
    }

    @Configuration
    static class ParentListenerConfig {

        @Bean
        EventLog eventLog() {
            return new EventLog();
        }

        // 이 컴포넌트는 리스너 메서드가 아니라 ApplicationListener<E> 빈이므로 E는 반드시
        // ApplicationEvent여야 한다 - 순수 POJO 페이로드는 PayloadApplicationEvent로 감싸서
        // 받는다(@EventListener라면 페이로드 타입을 그대로 파라미터로 받을 수 있는 것과 다르다).
        @Bean
        ApplicationListener<PayloadApplicationEvent<OrderCompletedEvent>> parentListener(EventLog eventLog) {
            return event -> eventLog.record("parent-received-" + event.getPayload().orderId());
        }
    }
}
