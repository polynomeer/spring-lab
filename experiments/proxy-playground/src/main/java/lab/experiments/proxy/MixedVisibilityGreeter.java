package lab.experiments.proxy;

// final 메서드와 private 메서드를 함께 가진 클래스 - CGLIB는 클래스 자체는 서브클래싱할 수
// 있지만, final 메서드는 오버라이드할 수 없고 private 메서드는 애초에 오버라이드 대상이
// 아니다. 두 경우 모두 어드바이스가 걸리지 않는 메서드가 있다는 것을 보여주기 위한 클래스다.
public class MixedVisibilityGreeter {

    public String greet(String name) {
        return "Hey, " + greetPrivately(name);
    }

    public final String greetFinally(String name) {
        return "Final hey, " + name;
    }

    private String greetPrivately(String name) {
        return name;
    }
}
