package lab.experiments.validation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// 파라미터 검증이 대상 메서드 호출 "전"에 막았는지, 아니면 대상 메서드가 실제로 실행됐는지를
// 구분하기 위한 계측 레이어 - cache-abstraction-lab의 InvocationCounter와 같은 역할.
public final class InvocationCounter {

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public int increment(String key) {
        return counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    public int count(String key) {
        AtomicInteger counter = counts.get(key);
        return counter == null ? 0 : counter.get();
    }
}
