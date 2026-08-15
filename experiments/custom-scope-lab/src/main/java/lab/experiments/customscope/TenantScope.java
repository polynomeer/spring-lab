package lab.experiments.customscope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;

// "테넌트 하나당 빈 인스턴스 하나"를 구현하는 Scope. 실제 저장/조회는 Scope 구현체 자신의
// 책임이다 - Spring 컨테이너는 스코프 이름과 objectFactory만 넘겨줄 뿐, 캐싱 여부·정리
// 시점은 전부 이 클래스가 결정한다.
public class TenantScope implements Scope {

    private final Map<String, Map<String, Object>> beansByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Runnable>> destructionCallbacksByTenant = new ConcurrentHashMap<>();
    private final AtomicInteger objectFactoryInvocationCount = new AtomicInteger();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, Object> beans = beansByTenant.computeIfAbsent(requireTenant(), t -> new ConcurrentHashMap<>());
        return beans.computeIfAbsent(name, n -> {
            objectFactoryInvocationCount.incrementAndGet();
            return objectFactory.getObject();
        });
    }

    @Override
    @Nullable
    public Object remove(String name) {
        String tenant = requireTenant();
        Map<String, Runnable> callbacks = destructionCallbacksByTenant.get(tenant);
        if (callbacks != null) {
            callbacks.remove(name);
        }
        Map<String, Object> beans = beansByTenant.get(tenant);
        return (beans != null) ? beans.remove(name) : null;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // AbstractBeanFactory#registerDisposableBeanIfNecessary()가 싱글턴이 아닌 커스텀
        // 스코프 빈에 대해 자동으로 호출해 주는 지점 - DisposableBean/@PreDestroy/커스텀
        // destroy 메서드를 전부 감싼 Runnable 하나를 그대로 넘겨준다. 그걸 "언제" 실행할지는
        // 전적으로 이 스코프의 몫이다 - 여기 저장해 두기만 하고, endTenant()가 호출되기
        // 전까지는 아무 일도 하지 않는다.
        destructionCallbacksByTenant.computeIfAbsent(requireTenant(), t -> new ConcurrentHashMap<>())
                .put(name, callback);
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

    // 테넌트의 "대화"가 끝났다고 이 스코프에 알려주는, Spring이 아니라 애플리케이션 코드가
    // 직접 호출해야 하는 메서드 - registerDestructionCallback()이 저장해 둔 Runnable들을
    // 이제야 실행한다.
    public void endTenant(String tenantId) {
        Map<String, Runnable> callbacks = destructionCallbacksByTenant.remove(tenantId);
        if (callbacks != null) {
            callbacks.values().forEach(Runnable::run);
        }
        beansByTenant.remove(tenantId);
    }

    public int objectFactoryInvocationCount() {
        return objectFactoryInvocationCount.get();
    }

    private String requireTenant() {
        String tenant = TenantContext.getTenant();
        if (tenant == null) {
            throw new IllegalStateException("no tenant bound to the current thread");
        }
        return tenant;
    }
}
