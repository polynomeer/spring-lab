package lab.experiments.scopedproxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;

// 테넌트 하나당 빈 인스턴스 하나를 캐싱하는 Scope(36번과 동일한 패턴). 이번 실험의
// 핵심은 이 클래스가 아니라, 이 스코프의 get()이 "현재 테넌트가 없으면 예외"라는
// 사실이 스코프드 프록시 유무에 따라 "언제" 드러나는가다.
public class TenantScope implements Scope {

    private final Map<String, Map<String, Object>> beansByTenant = new ConcurrentHashMap<>();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, Object> beans = beansByTenant.computeIfAbsent(requireTenant(), t -> new ConcurrentHashMap<>());
        return beans.computeIfAbsent(name, n -> objectFactory.getObject());
    }

    @Override
    @Nullable
    public Object remove(String name) {
        Map<String, Object> beans = beansByTenant.get(requireTenant());
        return (beans != null) ? beans.remove(name) : null;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // 이번 실험에서는 소멸 콜백 타이밍을 다루지 않는다(36번에서 이미 확인함) - 무시한다.
    }

    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
        return "tenantId".equals(key) ? requireTenant() : null;
    }

    @Override
    public String getConversationId() {
        return requireTenant();
    }

    private String requireTenant() {
        String tenant = TenantContext.getTenant();
        if (tenant == null) {
            throw new IllegalStateException("no tenant bound to the current thread");
        }
        return tenant;
    }
}
