package lab.experiments.event;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventLog {

    public record Entry(String label, String threadName) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String label) {
        entries.add(new Entry(label, Thread.currentThread().getName()));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }

    // @Async 리스너는 발행자 스레드가 아닌 별도 스레드에서 실행되므로, 발행 메서드가 반환된
    // 뒤에도 기록이 아직 없을 수 있다 - 타임아웃 안에서 폴링해서 기다린다.
    public boolean awaitAtLeast(int count, Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (entries.size() < count) {
            if (System.nanoTime() > deadlineNanos) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
