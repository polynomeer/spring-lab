package lab.experiments.cache;

public interface ProductLookupService {

    Product findById(String id);

    Product findByIdExpensiveFiltered(String id);

    Product findByIdConditional(String id, boolean useCache);

    Product save(Product product);

    void evict(String id);

    void evictAll();

    Product findByIdMayFail(String id, boolean fail);

    Product findByIdSlowSync(String id);

    Product refreshViaSelfInvocation(String id);

    Product findByIdRaw(String id);

    String findNameRaw(String id);
}
