package lab.experiments.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

public class ProductLookupServiceImpl implements ProductLookupService {

    private final InvocationCounter counter;

    public ProductLookupServiceImpl(InvocationCounter counter) {
        this.counter = counter;
    }

    @Override
    @Cacheable(cacheNames = "products", key = "#id")
    public Product findById(String id) {
        counter.increment("findById");
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    @Cacheable(cacheNames = "products-unless", key = "#id", unless = "#result.price() > 1000")
    public Product findByIdExpensiveFiltered(String id) {
        counter.increment("findByIdExpensiveFiltered");
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    @Cacheable(cacheNames = "products-condition", key = "#id", condition = "#useCache")
    public Product findByIdConditional(String id, boolean useCache) {
        counter.increment("findByIdConditional");
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    @CachePut(cacheNames = "products", key = "#product.id()")
    public Product save(Product product) {
        counter.increment("save");
        return product;
    }

    @Override
    @CacheEvict(cacheNames = "products", key = "#id")
    public void evict(String id) {
        counter.increment("evict");
    }

    @Override
    @CacheEvict(cacheNames = "products", allEntries = true)
    public void evictAll() {
        counter.increment("evictAll");
    }

    @Override
    @Cacheable(cacheNames = "products-fail", key = "#id")
    public Product findByIdMayFail(String id, boolean fail) {
        counter.increment("findByIdMayFail");
        if (fail) {
            throw new IllegalStateException("lookup failed for " + id);
        }
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    @Cacheable(cacheNames = "products-sync", key = "#id", sync = true)
    public Product findByIdSlowSync(String id) {
        counter.increment("findByIdSlowSync");
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    public Product refreshViaSelfInvocation(String id) {
        // this.findById(id)는 프록시가 아니라 대상 객체를 직접 호출한다 - findById()에 붙은
        // @Cacheable 어드바이스 자체가 실행되지 않으므로, 읽기도 쓰기도 일어나지 않는다.
        return this.findById(id);
    }

    @Override
    @Cacheable(cacheNames = "collision-cache")
    public Product findByIdRaw(String id) {
        counter.increment("findByIdRaw");
        return new Product(id, "product-" + id, priceFor(id));
    }

    @Override
    @Cacheable(cacheNames = "collision-cache")
    public String findNameRaw(String id) {
        counter.increment("findNameRaw");
        return "name-" + id;
    }

    private long priceFor(String id) {
        return "expensive".equals(id) ? 5000L : 100L;
    }
}
