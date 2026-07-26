package lab.experiments.proxy;

// 인터페이스를 구현하지 않는다 - JDK Dynamic Proxy는 인터페이스가 있어야만 만들 수 있으므로,
// 이 클래스는 ProxyFactory가 CGLIB로 넘어갈 수밖에 없는 상황을 재현하는 용도다.
public class PlainGreeter {

    public String greet(String name) {
        return "Hi, " + name;
    }
}
