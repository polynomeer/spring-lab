package lab.ext.timing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TimingLog {

    private static final List<String> measuredMethodNames = new CopyOnWriteArrayList<>();

    private TimingLog() {
    }

    public static void record(String methodName, long elapsedNanos) {
        measuredMethodNames.add(methodName);
    }

    public static List<String> measuredMethodNames() {
        return List.copyOf(measuredMethodNames);
    }

    public static void reset() {
        measuredMethodNames.clear();
    }
}
