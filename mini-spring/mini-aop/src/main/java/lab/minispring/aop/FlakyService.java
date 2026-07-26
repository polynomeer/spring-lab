package lab.minispring.aop;

public class FlakyService implements Flaky {

    private final int failUntilAttempt;
    private int attempts = 0;

    public FlakyService(int failUntilAttempt) {
        this.failUntilAttempt = failUntilAttempt;
    }

    @Override
    public String call() {
        attempts++;
        if (attempts < failUntilAttempt) {
            throw new IllegalStateException("not yet, attempt " + attempts);
        }
        return "success after " + attempts + " attempts";
    }

    public int getAttempts() {
        return attempts;
    }
}
