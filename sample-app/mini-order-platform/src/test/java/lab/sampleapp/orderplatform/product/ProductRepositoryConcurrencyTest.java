package lab.sampleapp.orderplatform.product;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 11.2절이 주장한 "UPDATE ... WHERE stock &gt;= ? 한 문장이면 락을 직접 잡지 않아도
 * 원자적이다"를 순차 호출이 아니라 진짜 동시 스레드로 재현해서 검증한다 - 지금까지 이
 * 모듈의 어떤 테스트도 실제 경쟁 조건(race condition)을 만들어 본 적이 없었다.
 *
 * <p>H2 2.x는 기본적으로 MVCC를 쓰고, UPDATE 문은 격리 수준과 무관하게 항상 쓰기 락을
 * 잡는다 - 그래서 같은 행을 동시에 갱신하려는 두 번째 스레드는 예외 없이 첫 번째 스레드의
 * 트랜잭션이 끝날 때까지 그냥 대기했다가, 그 이후의(이미 줄어든) 재고를 놓고 다시
 * WHERE 조건을 평가한다 - 애플리케이션 코드가 락을 직접 잡을 필요가 없는 이유다.
 */
class ProductRepositoryConcurrencyTest {

    private AnnotationConfigApplicationContext context;

    private ProductRepository buildRepository() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        return context.getBean(ProductRepository.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void exactlyAsManyConcurrentDecrementsSucceedAsThereIsStock() throws Exception {
        ProductRepository repository = buildRepository();
        long productId = 1L;
        int initialStock = 3;
        int concurrentAttempts = 8; // 재고보다 훨씬 많은 스레드가 동시에 경쟁한다
        repository.save(new Product(productId, "hot-item", 1_000, initialStock));

        ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);
        CountDownLatch ready = new CountDownLatch(concurrentAttempts);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentAttempts; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(); // 모든 스레드가 준비될 때까지 기다렸다가 한꺼번에 출발한다
                return repository.decreaseStock(productId, 1);
            });
        }

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(executor.submit(task));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        long successCount = futures.stream()
                .map(future -> {
                    try {
                        return future.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Boolean::booleanValue)
                .count();

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // 재고보다 많은 스레드가 경쟁했어도, 성공한 수는 정확히 초기 재고만큼이다 -
        // 실패한 나머지는 예외 없이 그냥 false를 돌려받는다(재시도할지는 호출자의 몫).
        assertThat(successCount).isEqualTo(initialStock);
        assertThat(repository.findById(productId)).map(Product::stock).contains(0);
    }

    @Test
    void decrementsAreNeverLostEvenUnderHeavyContentionOnAmpleStock() throws Exception {
        // 재고가 충분해서 아무도 거절당하지 않는 경우에도, 동시에 들어온 갱신이 서로를
        // 덮어써서 몇 건이 유실되는 일(잘못 짠 "조회 후 갱신" 코드에서 흔한 실수)이 없는지
        // 확인한다 - 성공 건수와 실제 차감된 재고가 항상 정확히 일치해야 한다.
        ProductRepository repository = buildRepository();
        long productId = 2L;
        int initialStock = 100;
        int concurrentAttempts = 50;
        repository.save(new Product(productId, "ample-item", 1_000, initialStock));

        ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);
        AtomicLong successCount = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentAttempts; i++) {
            futures.add(executor.submit(() -> {
                try {
                    start.await();
                    if (repository.decreaseStock(productId, 1)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(concurrentAttempts);
        assertThat(repository.findById(productId)).map(Product::stock).contains(initialStock - concurrentAttempts);
    }
}
