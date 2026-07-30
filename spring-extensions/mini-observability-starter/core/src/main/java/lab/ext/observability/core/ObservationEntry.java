package lab.ext.observability.core;

public record ObservationEntry(String path, long elapsedMillis, boolean slow) {
}
