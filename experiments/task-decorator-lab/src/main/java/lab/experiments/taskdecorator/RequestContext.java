package lab.experiments.taskdecorator;

// ThreadLocal은 스레드 경계를 절대 스스로 넘지 않는다 - @Async가 스레드 풀의 다른 스레드에서
// 대상 메서드를 실행하는 이상, 호출자가 여기 설정해 둔 값은 기본적으로 그 실행 스레드에
// 보이지 않는다. TaskDecorator가 정확히 이 경계를 메우기 위한 지점이다.
public final class RequestContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
