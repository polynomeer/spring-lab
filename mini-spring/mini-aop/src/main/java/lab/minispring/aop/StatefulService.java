package lab.minispring.aop;

/**
 * 일부러 어떤 인터페이스도 구현하지 않는다 - {@link MiniSubclassProxyFactory}가 다루는
 * 대상이 바로 이런 클래스다({@link MiniProxyFactory}는 이런 클래스를 프록시할 방법이
 * 없다). {@code greet()}는 오버라이드 가능해서 가로채지고, {@code finalGreet()}는
 * final이라 가로채지지 않는다 - 둘 다 같은 {@code label} 필드를 읽는다는 점이 중요하다.
 */
public class StatefulService {

    private String label = "default";

    public void setLabel(String label) {
        this.label = label;
    }

    public String greet() {
        return "hello, " + label;
    }

    public final String finalGreet() {
        return "final hello, " + label;
    }

    private String secret() {
        return "secret, " + label;
    }

    String callSecret() {
        return secret();
    }
}
