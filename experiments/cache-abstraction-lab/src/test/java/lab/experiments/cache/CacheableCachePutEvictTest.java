package lab.experiments.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class CacheableCachePutEvictTest {

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
    void cacheHitAvoidsSecondInvocation() {
        Product first = service.findById("p1");
        Product second = service.findById("p1");

        assertThat(first).isEqualTo(second);
        assertThat(counter.count("findById")).isEqualTo(1);
    }

    @Test
    void differentKeysInvokeTheTargetSeparately() {
        service.findById("p1");
        service.findById("p2");

        assertThat(counter.count("findById")).isEqualTo(2);
    }

    @Test
    void cachePutAlwaysInvokesTheTargetAndOverwritesTheCachedEntry() {
        service.findById("p1");
        assertThat(counter.count("findById")).isEqualTo(1);

        Product updated = new Product("p1", "renamed", 999);
        Product saved = service.save(updated);
        assertThat(saved).isEqualTo(updated);
        assertThat(counter.count("save")).isEqualTo(1);

        // save()는 findById()와 같은 캐시("products")·같은 키(#id / #product.id())를 쓴다 -
        // @CachePut이 써 둔 값을 findById()의 @Cacheable이 그대로 읽어서, 재계산 없이도
        // 갱신된 값을 돌려준다.
        Product afterSave = service.findById("p1");
        assertThat(afterSave).isEqualTo(updated);
        assertThat(counter.count("findById")).isEqualTo(1);
    }

    @Test
    void evictRemovesOnlyTheGivenKey() {
        service.findById("p1");
        service.findById("p2");
        assertThat(counter.count("findById")).isEqualTo(2);

        service.evict("p1");

        service.findById("p1");
        service.findById("p2");
        assertThat(counter.count("findById")).isEqualTo(3);
    }

    @Test
    void evictAllClearsEveryEntryInTheCache() {
        service.findById("p1");
        service.findById("p2");
        assertThat(counter.count("findById")).isEqualTo(2);

        service.evictAll();

        service.findById("p1");
        service.findById("p2");
        assertThat(counter.count("findById")).isEqualTo(4);
    }

    @Test
    void unlessExcludesMatchingResultsFromTheCacheAfterInvocation() {
        service.findByIdExpensiveFiltered("expensive");
        service.findByIdExpensiveFiltered("expensive");
        // unless는 매번 대상 메서드를 부른 "뒤"에 결과를 보고 저장 여부를 결정한다 -
        // 두 번 다 실제로 호출된다.
        assertThat(counter.count("findByIdExpensiveFiltered")).isEqualTo(2);

        service.findByIdExpensiveFiltered("cheap");
        service.findByIdExpensiveFiltered("cheap");
        // 조건을 통과한 결과는 정상적으로 캐시된다 - 두 번째 호출은 히트.
        assertThat(counter.count("findByIdExpensiveFiltered")).isEqualTo(3);
    }

    @Test
    void conditionSkipsCachingEntirelyBasedOnTheMethodArgument() {
        service.findByIdConditional("p1", false);
        service.findByIdConditional("p1", false);
        // condition은 호출 "전"에 평가된다 - false면 캐시 조회/저장 자체가 일어나지 않는다.
        assertThat(counter.count("findByIdConditional")).isEqualTo(2);

        service.findByIdConditional("p1", true);
        service.findByIdConditional("p1", true);
        assertThat(counter.count("findByIdConditional")).isEqualTo(3);
    }
}
