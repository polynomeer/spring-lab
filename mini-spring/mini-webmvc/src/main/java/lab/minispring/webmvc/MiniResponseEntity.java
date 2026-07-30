package lab.minispring.webmvc;

public record MiniResponseEntity<T>(int status, T body) {

    public static <T> MiniResponseEntity<T> ok(T body) {
        return new MiniResponseEntity<>(200, body);
    }

    public static <T> MiniResponseEntity<T> status(int status, T body) {
        return new MiniResponseEntity<>(status, body);
    }
}
