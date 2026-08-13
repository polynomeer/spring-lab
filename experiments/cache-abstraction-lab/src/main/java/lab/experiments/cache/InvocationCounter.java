package lab.experiments.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// 실제 대상 메서드가 몇 번 실행됐는지 - 즉 캐시를 "우회"했는지를 관찰하기 위한 계측 레이어.
// mini-webmvc의 CountingArgumentResolver, mini-component-scan의 RecordingClassLoader와
// 같은 역할이다 - 반환값만으로는 캐시 히트인지 재계산인지 구분할 수 없다.
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
