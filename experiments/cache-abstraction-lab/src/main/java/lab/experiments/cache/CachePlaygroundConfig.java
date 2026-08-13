package lab.experiments.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachePlaygroundConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    @Bean
    public InvocationCounter invocationCounter() {
        return new InvocationCounter();
    }

    @Bean
    public ProductLookupService productLookupService(InvocationCounter invocationCounter) {
        return new ProductLookupServiceImpl(invocationCounter);
    }
}
