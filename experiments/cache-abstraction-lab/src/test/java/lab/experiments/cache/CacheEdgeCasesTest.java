package lab.experiments.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheEdgeCasesTest {

    private AnnotationConfigApplicationContext context;
    private ProductLookupService service;
    private InvocationCounter counter;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(CachePlaygroundConfig.class);
        service = context.getBean(ProductLookupService.class);
        counter = context.getBean(InvocationCounter.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void selfInvocationBypassesTheCachingProxyEntirely() {
        service.findById("p1");
        assertThat(counter.count("findById")).isEqualTo(1);

        // refreshViaSelfInvocation()은 캐시되지 않은 평범한 메서드지만, 내부에서
        // this.findById(id)를 직접 호출한다 - 프록시를 거치지 않으므로 @Cacheable 자체가
        // 적용되지 않는다. 이미 캐시된 키인데도 대상 메서드가 다시 실행된다.
        service.refreshViaSelfInvocation("p1");
        assertThat(counter.count("findById")).isEqualTo(2);

        // 그 self-invocation 호출은 읽기도 쓰기도 캐시를 거치지 않았으므로, 최초 호출이 남긴
        // 캐시 엔트리는 그대로다 - 프록시를 통한 세 번째 호출은 다시 히트한다.
        service.findById("p1");
        assertThat(counter.count("findById")).isEqualTo(2);
    }

    @Test
    void exceptionsFromTheTargetAreNeverCached() {
        assertThatThrownBy(() -> service.findByIdMayFail("p1", true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter.count("findByIdMayFail")).isEqualTo(1);

        // 실패한 호출은 캐시에 아무것도 남기지 않는다 - 다음 호출도 다시 대상 메서드를 탄다.
        Product result = service.findByIdMayFail("p1", false);
        assertThat(counter.count("findByIdMayFail")).isEqualTo(2);
        assertThat(result.id()).isEqualTo("p1");
    }

    @Test
    void defaultKeyGeneratorCollidesAcrossUnrelatedMethodsSharingACacheName() {
        // SimpleKeyGenerator#generate(Object target, Method method, Object... params)는
        // method 파라미터를 전혀 쓰지 않는다 - 인자가 하나면 그 인자 자체를 키로 그대로
        // 쓴다. findByIdRaw(String)과 findNameRaw(String)이 같은 캐시 이름("collision-cache")과
        // 같은 단일 String 인자를 쓰므로, 어느 메서드가 먼저 채워 넣었는지에 따라 키가
        // 그대로 충돌한다.
        service.findByIdRaw("p1");
        assertThat(counter.count("findByIdRaw")).isEqualTo(1);

        // findNameRaw("p1")은 캐시를 조회했다가 findByIdRaw가 남긴 Product를 String으로
        // 착각하고 반환하려다 캐스팅에 실패한다 - 대상 메서드는 호출조차 되지 않았다(히트였다).
        assertThatThrownBy(() -> service.findNameRaw("p1")).isInstanceOf(ClassCastException.class);
        assertThat(counter.count("findNameRaw")).isEqualTo(0);
    }

    @Test
    void syncAttributeGuaranteesTheTargetRunsOnlyOnceUnderConcurrentAccess() throws Exception {
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Product>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return service.findByIdSlowSync("shared");
                }));
            }
            for (Future<Product> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        // ConcurrentMapCache#get(key, Callable)는 ConcurrentHashMap#computeIfAbsent로
        // 구현돼 있다 - 같은 키에 대한 매핑 함수는 원자적으로 딱 한 번만 실행된다. sync=true는
        // CacheAspectSupport#executeSynchronized()를 거쳐 바로 이 경로를 타므로, 동시에 들어온
        // 8개 호출 중 실제로 대상 메서드를 실행하는 건 하나뿐이고 나머지는 그 결과를 기다렸다가
        // 받는다.
        assertThat(counter.count("findByIdSlowSync")).isEqualTo(1);
    }
}
