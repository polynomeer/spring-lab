package lab.minispring.event;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab.minispring.transaction.JdbcMiniTransactionManager;
import lab.minispring.transaction.MiniPropagation;
import lab.minispring.transaction.MiniTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniEventMulticasterTest {

    interface OrderEvent {
        long orderId();
    }

    record OrderPlacedEvent(long orderId) implements OrderEvent {
    }

    record OrderShippedEvent(long orderId) implements OrderEvent {
    }

    private final List<String> log = new CopyOnWriteArrayList<>();
    private JdbcMiniTransactionManager transactionManager;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DataSource dataSource = ds;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE dummy (id INT PRIMARY KEY)");
        }
        transactionManager = new JdbcMiniTransactionManager(dataSource);
    }

    @Test
    void listenersRunInAscendingOrderOnThePublisherThread() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("second"), 20);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("first"), 10);

        multicaster.publish(new OrderPlacedEvent(1));

        assertThat(log).containsExactly("first", "second");
    }

    @Test
    void listenerRegisteredForASupertypeReceivesEveryImplementingSubtype() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderEvent.class, e -> log.add("order-event:" + e.orderId()), 1);

        multicaster.publish(new OrderPlacedEvent(1));
        multicaster.publish(new OrderShippedEvent(2));

        assertThat(log).containsExactly("order-event:1", "order-event:2");
    }

    @Test
    void defaultErrorPolicyRethrowsAndAbortsLaterListeners() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> {
            throw new IllegalStateException("boom");
        }, 10);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("never reached"), 20);

        assertThatThrownBy(() -> multicaster.publish(new OrderPlacedEvent(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(log).isEmpty();
    }

    @Test
    void customErrorPolicyCanSwallowAndLetLaterListenersRun() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.setErrorPolicy(ex -> log.add("swallowed:" + ex.getMessage()));
        multicaster.addListener(OrderPlacedEvent.class, e -> {
            throw new IllegalStateException("boom");
        }, 10);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("reached"), 20);

        multicaster.publish(new OrderPlacedEvent(1));

        assertThat(log).containsExactly("swallowed:boom", "reached");
    }

    @Test
    void asyncListenerRunsOnADifferentThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> asyncThread = new CopyOnWriteArrayList<>();
        MiniEventMulticaster multicaster =
                new MiniEventMulticaster(Executors.newSingleThreadExecutor(), transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> {
            asyncThread.add(Thread.currentThread().getName());
            latch.countDown();
        }, 10, true, false);

        multicaster.publish(new OrderPlacedEvent(1));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncThread).doesNotContain(Thread.currentThread().getName());
    }

    @Test
    void afterCommitListenerFiresOnlyOnceTheMiniTransactionActuallyCommits() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("after-commit:" + e.orderId()), 10, false, true);

        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);
        multicaster.publish(new OrderPlacedEvent(1));
        assertThat(log).isEmpty();

        transactionManager.commit(status);
        assertThat(log).containsExactly("after-commit:1");
    }

    @Test
    void afterCommitListenerNeverFiresWhenTheMiniTransactionRollsBack() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("after-commit:" + e.orderId()), 10, false, true);

        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);
        multicaster.publish(new OrderPlacedEvent(1));
        transactionManager.rollback(status);

        assertThat(log).isEmpty();
    }

    @Test
    void afterCommitListenerIsSilentlyDroppedWhenNoTransactionIsActive() {
        MiniEventMulticaster multicaster = new MiniEventMulticaster(Runnable::run, transactionManager);
        multicaster.addListener(OrderPlacedEvent.class, e -> log.add("after-commit:" + e.orderId()), 10, false, true);

        multicaster.publish(new OrderPlacedEvent(1));

        assertThat(log).isEmpty();
    }
}
