package lab.sampleapp.orderplatform.aop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyStore {

    private final Map<String, Object> resultsByKey = new ConcurrentHashMap<>();

    /** 이미 같은 키로 계산해 둔 결과가 있으면 그걸 돌려주고, 없으면 supplier를 실행해 결과를 새로 저장한다. */
    @SuppressWarnings("unchecked")
    <T> T computeIfAbsent(String key, java.util.function.Supplier<T> supplier) {
        return (T) resultsByKey.computeIfAbsent(key, k -> supplier.get());
    }

    public boolean hasSeen(String key) {
        return resultsByKey.containsKey(key);
    }
}
