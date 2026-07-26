package lab.experiments.proxy;

// final 클래스 - CGLIB는 서브클래싱으로 프록시를 만들기 때문에 final 클래스는 원천적으로
// 프록시할 수 없다.
public final class FinalGreeter {

    public String greet(String name) {
        return "Yo, " + name;
    }
}
